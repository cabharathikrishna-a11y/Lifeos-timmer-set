const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

const RTDB_URL = 'https://cloud-storage-f8ab3-default-rtdb.asia-southeast1.firebasedatabase.app/';
const STORAGE_BUCKET_NAME = 'cloud-storage-f8ab3.firebasestorage.app';

/**
 * Monthly Job 1: Transfer RTDB Messages older than 1 Month (30 days) to Firebase Storage Bucket,
 * then purge them from RTDB live state.
 */
exports.monthlyTransferRtdbToStorage = functions.pubsub
  .schedule('0 0 1 * *') // Runs 1st of every month at midnight UTC
  .timeZone('UTC')
  .onRun(async (context) => {
    const db = admin.database(RTDB_URL);
    const bucket = admin.storage().bucket(STORAGE_BUCKET_NAME);
    const oneMonthAgoMs = Date.now() - (30 * 24 * 60 * 60 * 1000);

    console.log(`Starting monthly RTDB to Storage transfer for messages older than 30 days (${new Date(oneMonthAgoMs).toISOString()})`);

    try {
      const messagesRef = db.ref('community_chat/messages');
      const snapshot = await messagesRef.once('value');

      if (!snapshot.exists()) {
        console.log('No messages found in RTDB.');
        return null;
      }

      const archivedMessages = {};
      const updates = {};
      let archivedCount = 0;

      snapshot.forEach((msgNode) => {
        const msg = msgNode.val();
        const timestamp = msg.timestamp || 0;

        if (timestamp && timestamp < oneMonthAgoMs) {
          archivedMessages[msgNode.key] = msg;
          updates[`community_chat/messages/${msgNode.key}`] = null;
          updates[`messages/group_main/${msgNode.key}`] = null;
          archivedCount++;
        }
      });

      if (archivedCount > 0) {
        const now = new Date();
        const yearMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
        const filePath = `chat_archives/${yearMonth}/archived_messages.json`;
        const file = bucket.file(filePath);

        await file.save(JSON.stringify(archivedMessages, null, 2), {
          contentType: 'application/json',
          metadata: {
            archivedAt: new Date().toISOString(),
            archivedCount: archivedCount.toString()
          }
        });

        await db.ref().update(updates);
        console.log(`Successfully transferred ${archivedCount} messages to ${STORAGE_BUCKET_NAME}/${filePath} and purged from RTDB.`);
      } else {
        console.log('No messages older than 30 days to transfer.');
      }
    } catch (error) {
      console.error('Error in monthlyTransferRtdbToStorage function:', error);
    }
    return null;
  });

/**
 * Monthly Job 2: Delete monthly archive folders/files in Storage Bucket older than 12 months.
 */
exports.monthlyPurgeStorageBucketArchives = functions.pubsub
  .schedule('0 1 1 * *') // Runs 1st of every month at 01:00 UTC
  .timeZone('UTC')
  .onRun(async (context) => {
    const bucket = admin.storage().bucket(STORAGE_BUCKET_NAME);
    const twelveMonthsAgoMs = Date.now() - (365 * 24 * 60 * 60 * 1000);

    console.log(`Starting Storage Bucket cleanup for archives older than 12 months (${new Date(twelveMonthsAgoMs).toISOString()})`);

    try {
      const [files] = await bucket.getFiles({ prefix: 'chat_archives/' });

      for (const file of files) {
        const [metadata] = await file.getMetadata();
        const createdMs = new Date(metadata.timeCreated).getTime();

        if (createdMs < twelveMonthsAgoMs) {
          await file.delete();
          console.log(`Deleted 12-month-old archive file from Storage Bucket: ${file.name}`);
        }
      }
    } catch (error) {
      console.error('Error in monthlyPurgeStorageBucketArchives function:', error);
    }
    return null;
  });

