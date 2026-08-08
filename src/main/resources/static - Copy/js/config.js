// API Configuration
const API_URL = 'http://localhost:8080';
const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB chunks

// Global state
let jwtToken = localStorage.getItem('jwtToken');
let currentFolderId = null;
let currentView = 'my-drive';
let allFiles = [];
let selectedItems = new Set();
let uploadQueue = [];
let activeUploads = new Map();
let contextMenuItem = null;
let pendingAction = null;
let pendingItems = [];