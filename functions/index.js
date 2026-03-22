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

  // 1. Prepare Customer and Device Numbers
  const customerCC = data.customerCountryCode || data.countryCode || "";
  const customerNum = data.customerNumber || data.phoneNumber || "";
  const customerPhone = `${customerCC}-${customerNum}`;

  const deviceCC = data.dialCountryCode || "";
  const deviceNum = data.dialNumber || "";
  const fullDeviceNumber = `${deviceCC}${deviceNum}`;

  try {
    // Helper: Normalize phone numbers (digits only)
    const normalize = (num) => (num || "").replace(/\D/g, "");
    const normalizedDeviceNum = normalize(fullDeviceNumber);

    // Step 1: Retrieve Lead by Phone Number
    const retrieveUrl = `${LSQ_BASE_URL}/LeadManagement.svc/RetrieveLeadByPhoneNumber?phone=${encodeURIComponent(customerPhone)}`;
    const leadResponse = await axios.get(retrieveUrl, {
      headers: { "x-LSQ-AccessKey": LSQ_ACCESS_KEY, "x-LSQ-SecretKey": LSQ_SECRET_KEY },
    });

    const leads = leadResponse.data;
    if (!leads || leads.length === 0) {
      console.log(`No lead found for ${customerPhone}. Purging...`);
      if (data.audioDownloadUrl) {
          try {
              const url = new URL(data.audioDownloadUrl);
              const pathParts = url.pathname.split("/o/")[1];
              const filePath = decodeURIComponent(pathParts.split("?")[0]);
              await bucket.file(filePath).delete();
          } catch (e) {}
      }
      await db.collection("call_logs").doc(docId).delete();
      return;
    }

    // Found Lead
    const prospectId = leads[0].ProspectID;
    const leadOwnerId = leads[0].OwnerID; // Note: LSQ uses OwnerID (capital ID)
    console.log(`Lead: ${prospectId}, Owner: ${leadOwnerId} for ${customerPhone}`);

    // Step 2: Retrieve Owner Details and Verify Number
    let isVerified = false;
    if (leadOwnerId) {
        const ownerUrl = `${LSQ_BASE_URL}/UserManagement.svc/User/Retrieve/ByUserId?userId=${leadOwnerId}`;
        try {
            const ownerResponse = await axios.get(ownerUrl, {
                headers: { "x-LSQ-AccessKey": LSQ_ACCESS_KEY, "x-LSQ-SecretKey": LSQ_SECRET_KEY }
            });
            const owners = ownerResponse.data;
            if (Array.isArray(owners) && owners.length > 0) {
                const owner = owners[0];
                const lsqOwnerPhone = normalize(owner.PhoneMain);
                
                console.log(`Verifying: Device [${normalizedDeviceNum}] vs Owner [${lsqOwnerPhone}]`);

                // Check if device number matches or is a suffix of LSQ number
                if (normalizedDeviceNum && lsqOwnerPhone && lsqOwnerPhone.endsWith(normalizedDeviceNum)) {
                    isVerified = true;
                    console.log("Verification Success");
                }
            }
        } catch (err) {
            console.error(`Owner Fetch Error: ${err.message}`);
        }
    }

    if (!isVerified) {
        console.warn("Skipping: Owner Mismatch");
        return db.collection("call_logs").doc(docId).update({ 
            lsqSyncStatus: "SKIPPED_OWNER_MISMATCH",
            ownerVerification: "FAILED"
        });
    }

    // Step 3: Create Activity
    const formatToLsqDate = (timestamp) => {
      if (!timestamp) return "";
      const date = new Date(timestamp);
      return date.toISOString().replace("T", " ").substring(0, 19);
    };

    const startTimeFmt = formatToLsqDate(data.startTime);
    const endTimeFmt = formatToLsqDate(data.endTime);
    const createUrl = `${LSQ_BASE_URL}/ProspectActivity.svc/Create`;
    
    let note = `Call via CloudTrack [${data.platform || "PSTN"}]\n`;
    note += `Device: ${fullDeviceNumber}\n`;
    note += `Customer: ${customerPhone}\n`;
    note += `Recording: ${data.audioDownloadUrl || "None"}`;

    const activityPayload = {
      RelatedProspectId: prospectId,
      ActivityEvent: 278,
      ActivityNote: note,
      ProcessFilesAsync: true,
      ActivityDateTime: endTimeFmt,
      OwnerId: leadOwnerId,
      Fields: [
        { SchemaName: "mx_Custom_1", Value: data.audioDownloadUrl ? "Recording Available" : "No Recording" },
        { SchemaName: "mx_Custom_2", Value: startTimeFmt },
        { SchemaName: "mx_Custom_3", Value: endTimeFmt },
        { SchemaName: "mx_Custom_4", Value: (data.durationSeconds || 0).toString() },
        { SchemaName: "mx_Custom_5", Value: data.callType || "" },
      ],
    };

    await axios.post(createUrl, activityPayload, {
      headers: {
        "x-LSQ-AccessKey": LSQ_ACCESS_KEY,
        "x-LSQ-SecretKey": LSQ_SECRET_KEY,
        "Content-Type": "application/json",
      },
    });

    console.log("Sync Success");
    return db.collection("call_logs").doc(docId).update({ 
        lsqSyncStatus: "SUCCESS", 
        lsqProspectId: prospectId,
        ownerVerification: "PASSED"
    });

  } catch (error) {
    console.error("Error Syncing to LeadSquared:", error.response ? error.response.data : error.message);
    return db.collection("call_logs").doc(docId).update({ lsqSyncStatus: "FAILED", lsqError: error.message });
  }
});
