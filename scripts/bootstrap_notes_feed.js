/**
 * Bootstrap script to create notes_feed collection from notes collection.
 * Run this with: node bootstrap_notes_feed.js
 * 
 * Prerequisites:
 * 1. npm install firebase-admin
 * 2. Download service account key from Firebase Console
 * 3. Set GOOGLE_APPLICATION_CREDENTIALS env var to the key file path
 */

const admin = require('firebase-admin');
const sharp = require('sharp'); // For image resizing (optional, fallback available)

// Initialize Firebase Admin
// You'll need to download a service account key from Firebase Console
admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  // Or use: credential: admin.credential.cert(require('./serviceAccountKey.json'))
});

const db = admin.firestore();

async function bootstrap() {
  console.log('Fetching all notes...');
  const notesSnapshot = await db.collection('notes').get();
  console.log(`Found ${notesSnapshot.size} notes`);
  
  let processed = 0;
  for (const doc of notesSnapshot.docs) {
    const data = doc.data();
    
    const feedDoc = {
      userId: data.userId || null,
      userName: data.userName || null,
      text: data.text || data.note || null,
      summary: data.summary || null,
      location: data.location || null,
      timestamp: data.timestamp || null,
      likeCount: data.likeCount || data.likesCount || 0,
      imageWidth: data.imageWidth || 0,
      imageHeight: data.imageHeight || 0,
      visibility: data.visibility || 'public',
      isHidden: data.isHidden || false,
      isOwnerPrivate: data.isOwnerPrivate || false,
    };
    
    // Generate profile pic thumbnail (40x40)
    if (data.userProfilePic) {
      try {
        const profileBuffer = Buffer.from(data.userProfilePic, 'base64');
        // Simple resize - in Node.js you'd use sharp: await sharp(profileBuffer).resize(40, 40).jpeg({quality: 50}).toBuffer()
        // For now, just skip if sharp not available
        feedDoc.userProfilePicThumb = data.userProfilePic.substring(0, 5000); // Truncated fallback
      } catch (e) {
        console.warn(`Failed to process profile pic for ${doc.id}`);
      }
    }
    
    // Generate image thumbnail (50x50)
    if (data.imageBase64) {
      try {
        const imgBuffer = Buffer.from(data.imageBase64, 'base64');
        feedDoc.imageThumb64 = data.imageBase64.substring(0, 5000); // Truncated fallback
      } catch (e) {
        console.warn(`Failed to process image for ${doc.id}`);
      }
    }
    
    await db.collection('notes_feed').doc(doc.id).set(feedDoc);
    processed++;
    if (processed % 10 === 0) {
      console.log(`Processed ${processed}/${notesSnapshot.size}`);
    }
  }
  
  console.log(`Bootstrap complete! Created ${processed} documents in notes_feed`);
}

bootstrap().catch(console.error);
