const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
const axios = require("axios");

initializeApp();
const db = getFirestore("cloudtrack");
const bucket = getStorage().bucket(); 

// Configuration
const LSQ_ACCESS_KEY = process.env.LSQ_ACCESS_KEY;
const LSQ_SECRET_KEY = process.env.LSQ_SECRET_KEY;
const LSQ_BASE_URL = "https://api-in21.leadsquared.com/v2";

/**
 * Triggered when a new call log is created in the 'cloudtrack' Firestore database (v2).
 */
exports.synccalltoleadsquared = onDocumentCreated({
    document: "call_logs/{docId}",
    database: "cloudtrack"
}, async (event) => {
    const data = event.data.data();
    const docId = event.params.docId;

    if (!data) {
        console.log("No data found in document");
        return;
    }

    console.log(`Processing New Call Log: ${docId}`);

    // Prioritize customer fields for LSQ search
    const countryCode = data.customerCountryCode || data.countryCode || "";
    const phoneNumber = data.customerNumber || data.phoneNumber || "";
    const phone = `${countryCode}-${phoneNumber}`;
    
    try {
      // Step 1: Retrieve Lead by Phone Number
      const retrieveUrl = `${LSQ_BASE_URL}/LeadManagement.svc/RetrieveLeadByPhoneNumber?phone=${encodeURIComponent(phone)}`;
      const leadResponse = await axios.get(retrieveUrl, {
        headers: {
          "x-LSQ-AccessKey": LSQ_ACCESS_KEY,
          "x-LSQ-SecretKey": LSQ_SECRET_KEY,
        },
      });

      const leads = leadResponse.data;
      if (!leads || leads.length === 0) {
        console.log(`No lead found in LSQ for phone: ${phone}. Cleaning up...`);
        
        // Purge if unknown
        if (data.audioDownloadUrl) {
            try {
                const url = new URL(data.audioDownloadUrl);
                const pathParts = url.pathname.split("/o/")[1];
                const filePath = decodeURIComponent(pathParts.split("?")[0]);
                await bucket.file(filePath).delete();
                console.log(`Deleted storage file: ${filePath}`);
            } catch (err) {
                console.warn(`Failed to delete storage file: ${err.message}`);
            }
        }
        await db.collection("call_logs").doc(docId).delete();
        console.log(`Purged log for unknown number: ${phone}`);
        return;
      }

      const prospectId = leads[0].ProspectID;
      console.log(`Found Lead: ${prospectId} for ${phone}`);

      // Formatting (Assuming Android gives UTC/Raw Epoch)
      const formatToLsqDate = (timestamp) => {
        if (!timestamp) return "";
        const date = new Date(timestamp);
        return date.toISOString().replace("T", " ").substring(0, 19);
      };

      const startTimeFormatted = formatToLsqDate(data.startTime);
      const endTimeFormatted = formatToLsqDate(data.endTime);

      // Step 3: Create Activity in LeadSquared
      const createActivityUrl = `${LSQ_BASE_URL}/ProspectActivity.svc/Create`;
      
      // Build detailed note
      let note = `Call via CloudTrack [${data.platform || "PSTN"}]\n`;
      note += `User: ${data.userCountryCode || ""}${data.userNumber || ""}\n`;
      note += `Customer: ${data.customerCountryCode || ""}${data.customerNumber || ""}\n`;
      note += `Recording Link: ${data.audioDownloadUrl || "No recording available"}`;

      const activityPayload = {
        RelatedProspectId: prospectId,
        ActivityEvent: 278,
        ActivityNote: note,
        ProcessFilesAsync: true,
        ActivityDateTime: endTimeFormatted,
        Fields: [
          { SchemaName: "mx_Custom_1", Value: data.audioDownloadUrl ? "Recording Available" : "No Recording" },
          { SchemaName: "mx_Custom_2", Value: startTimeFormatted },
          { SchemaName: "mx_Custom_3", Value: endTimeFormatted },
          { SchemaName: "mx_Custom_4", Value: (data.durationSeconds || 0).toString() },
          { SchemaName: "mx_Custom_5", Value: data.callType || "" },
        ],
      };

      const activityResponse = await axios.post(createActivityUrl, activityPayload, {
        headers: {
          "x-LSQ-AccessKey": LSQ_ACCESS_KEY,
          "x-LSQ-SecretKey": LSQ_SECRET_KEY,
          "Content-Type": "application/json",
        },
      });

      console.log("Activity Posted to LeadSquared Successfully:", activityResponse.data);
      
      return db.collection("call_logs").doc(docId).update({ lsqSyncStatus: "SUCCESS", lsqProspectId: prospectId });
      
    } catch (error) {
      console.error("Error Syncing to LeadSquared:", error.response ? error.response.data : error.message);
      return db.collection("call_logs").doc(docId).update({ lsqSyncStatus: "FAILED", lsqError: error.message });
    }
});
