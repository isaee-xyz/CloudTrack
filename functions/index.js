const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
const axios = require("axios");

initializeApp();
const db = getFirestore("cloudtrack");
const bucket = getStorage().bucket(); // Default Storage bucket

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

    console.log(`Processing New Call Log (v2) on database 'cloudtrack': ${docId}`);

    // Formatting: +91-9610002444
    const phone = `${data.countryCode}-${data.phoneNumber}`;
    
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
        
        // 1. Delete Audio from Storage if it exists
        if (data.audioDownloadUrl) {
            try {
                // Extract filename from URL (e.g., call_recordings%2Ffile.m4a)
                const pathParts = new URL(data.audioDownloadUrl).pathname.split("/o/")[1];
                const filePath = decodeURIComponent(pathParts.split("?")[0]);
                await bucket.file(filePath).delete();
                console.log(`Deleted storage file: ${filePath}`);
            } catch (err) {
                console.warn(`Failed to delete storage file (maybe already gone?): ${err.message}`);
            }
        }

        // 2. Delete Firestore Document
        await db.collection("call_logs").doc(docId).delete();
        console.log(`Successfully purged log and recording for unknown number: ${phone}`);
        return;
      }

      const prospectId = leads[0].ProspectID;
      console.log(`Found Lead: ${prospectId} for ${phone}`);

      // Step 2: Format Timestamps to UTC (Subtracting 5:30 hours from IST)
      const formatToUtc = (istTimestamp) => {
        const utcDate = new Date(istTimestamp - 19800000);
        return utcDate.toISOString().replace("T", " ").substring(0, 19);
      };

      const startTimeUtc = formatToUtc(data.startTime);
      const endTimeUtc = formatToUtc(data.endTime);

      // Step 3: Create Activity in LeadSquared
      const createActivityUrl = `${LSQ_BASE_URL}/ProspectActivity.svc/Create`;
      const activityPayload = {
        RelatedProspectId: prospectId,
        ActivityEvent: 278,
        ActivityNote: `Call from Mobile. Audio Link: ${data.audioDownloadUrl || "No recording"}`,
        ProcessFilesAsync: true,
        ActivityDateTime: endTimeUtc,
        Fields: [
          { SchemaName: "mx_Custom_1", Value: "Recording Available" },
          { SchemaName: "mx_Custom_2", Value: startTimeUtc },
          { SchemaName: "mx_Custom_3", Value: endTimeUtc },
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
      
      // Update Sync Status in Firestore
      return db.collection("call_logs").doc(docId).update({ lsqSyncStatus: "SUCCESS", lsqProspectId: prospectId });
      
    } catch (error) {
      console.error("Error Syncing to LeadSquared:", error.response ? error.response.data : error.message);
      return db.collection("call_logs").doc(docId).update({ lsqSyncStatus: "FAILED", lsqError: error.message });
    }
});
