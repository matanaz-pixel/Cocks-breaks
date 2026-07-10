// אתחול Firebase משותף לכל דפי האפליקציה
const firebaseConfig = {
  apiKey: "AIzaSyAVpa8znErN1v8eixIYlbZVy5UZVC_DEZ8",
  authDomain: "roberta-s-breaks.firebaseapp.com",
  databaseURL: "https://roberta-s-breaks-default-rtdb.europe-west1.firebasedatabase.app",
  projectId: "roberta-s-breaks",
  storageBucket: "roberta-s-breaks.firebasestorage.app",
  messagingSenderId: "26150636103",
  appId: "1:26150636103:web:3978834464b868e33dbf62"
};

firebase.initializeApp(firebaseConfig);
const db = firebase.database();
