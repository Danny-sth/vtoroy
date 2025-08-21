// Jarvis AI Assistant - Frontend Application
class JarvisApp {
    constructor() {
        this.sessionId = this.generateSessionId();
        this.isOnline = false;
        this.isLoading = false;
        
        this.initializeElements();
        this.bindEvents();
        this.checkStatus();
        this.updateSessionDisplay();
    }

    // Initialize DOM elements
    initializeElements() {
        this.statusDot = document.getElementById('status-dot');
        this.statusText = document.getElementById('status-text');
        this.messagesContainer = document.getElementById('messages-container');
        this.messageInput = document.getElementById('message-input');
        this.sendButton = document.getElementById('send-button');
        this.loadingOverlay = document.getElementById('loading-overlay');
        this.currentSessionElement = document.getElementById('current-session');
        this.knowledgePanel = document.getElementById('knowledge-panel');
        this.knowledgeClose = document.getElementById('knowledge-close');
        this.syncButton = document.getElementById('sync-button');
        this.knowledgeStats = document.getElementById('knowledge-stats');
    }

    // Bind event listeners
    bindEvents() {
        // Send message on button click
        this.sendButton.addEventListener('click', () => this.sendMessage());
        
        // Send message on Ctrl+Enter
        this.messageInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && e.ctrlKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });
        
        // Enable/disable send button based on input
        this.messageInput.addEventListener('input', () => {
            this.updateSendButton();
            this.autoResizeTextarea();
        });
        
        // Knowledge panel events
        this.knowledgeClose.addEventListener('click', () => {
            this.knowledgePanel.classList.remove('show');
        });
        
        this.syncButton.addEventListener('click', () => this.syncKnowledge());
        
        // Auto-resize textarea
        this.messageInput.addEventListener('input', () => this.autoResizeTextarea());
    }

    // Generate unique session ID
    generateSessionId() {
        return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }

    // Update session display
    updateSessionDisplay() {
        if (this.currentSessionElement) {
            this.currentSessionElement.textContent = this.sessionId;
        }
    }

    // Auto-resize textarea
    autoResizeTextarea() {
        this.messageInput.style.height = 'auto';
        this.messageInput.style.height = Math.min(this.messageInput.scrollHeight, 120) + 'px';
    }

    // Update send button state
    updateSendButton() {
        const hasText = this.messageInput.value.trim().length > 0;
        this.sendButton.disabled = !hasText || this.isLoading || !this.isOnline;
    }

    // Check system status
    async checkStatus() {
        try {
            const response = await fetch('/actuator/health');
            const data = await response.json();
            
            this.isOnline = data.status === 'UP';
            this.updateStatusIndicator();
            
            // Check knowledge base status
            this.checkKnowledgeStatus();
            
        } catch (error) {
            console.error('Health check failed:', error);
            this.isOnline = false;
            this.updateStatusIndicator();
        }
        
        // Re-check every 30 seconds
        setTimeout(() => this.checkStatus(), 30000);
    }

    // Update status indicator
    updateStatusIndicator() {
        if (this.isOnline) {
            this.statusDot.classList.add('online');
            this.statusDot.classList.remove('offline');
            this.statusText.textContent = 'Онлайн';
        } else {
            this.statusDot.classList.add('offline');
            this.statusDot.classList.remove('online');
            this.statusText.textContent = 'Оффлайн';
        }
        
        this.updateSendButton();
    }

    // Check knowledge base status
    async checkKnowledgeStatus() {
        try {
            const response = await fetch('/api/knowledge/status');
            const data = await response.json();
            
            this.updateKnowledgeStats(data);
        } catch (error) {
            console.error('Knowledge status check failed:', error);
        }
    }

    // Update knowledge statistics
    updateKnowledgeStats(data) {
        const documentsCount = document.getElementById('documents-count');
        const lastSync = document.getElementById('last-sync');
        
        if (documentsCount) {
            documentsCount.textContent = data.totalFiles || '0';
        }
        
        if (lastSync) {
            const syncTime = data.lastSyncTime ? new Date(data.lastSyncTime).toLocaleString('ru') : 'Никогда';
            lastSync.textContent = syncTime;
        }
    }

    // Send message to Jarvis
    async sendMessage() {
        const message = this.messageInput.value.trim();
        if (!message || this.isLoading || !this.isOnline) return;

        // Clear input
        this.messageInput.value = '';
        this.autoResizeTextarea();
        this.updateSendButton();

        // Add user message to chat
        this.addMessage('user', message);

        // Show loading
        this.setLoading(true);

        try {
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    query: message,
                    sessionId: this.sessionId
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const data = await response.json();
            
            // Add assistant response to chat
            this.addMessage('assistant', data.response, data.metadata);

        } catch (error) {
            console.error('Send message failed:', error);
            this.addMessage('assistant', `Извините, произошла ошибка: ${error.message}`, { error: true });
        } finally {
            this.setLoading(false);
        }
    }

    // Add message to chat
    addMessage(role, content, metadata = null) {
        // Remove welcome message if it exists
        const welcomeMessage = this.messagesContainer.querySelector('.welcome-message');
        if (welcomeMessage) {
            welcomeMessage.remove();
        }

        const messageElement = document.createElement('div');
        messageElement.className = `message ${role}`;

        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = role === 'user' ? '👤' : '🤖';

        const messageContent = document.createElement('div');
        messageContent.className = 'message-content';
        messageContent.textContent = content;

        const messageTime = document.createElement('div');
        messageTime.className = 'message-time';
        messageTime.textContent = new Date().toLocaleTimeString('ru');

        messageContent.appendChild(messageTime);

        // Add metadata if available
        if (metadata) {
            const metadataElement = document.createElement('div');
            metadataElement.className = 'message-metadata';
            
            const metadataText = [];
            if (metadata.approach) {
                metadataText.push(`Подход: ${metadata.approach}`);
            }
            if (metadata.error) {
                metadataText.push('⚠️ Ошибка');
                messageContent.style.borderColor = 'var(--error)';
                messageContent.style.backgroundColor = 'rgba(239, 68, 68, 0.1)';
            }
            
            metadataElement.textContent = metadataText.join(' • ');
            messageContent.appendChild(metadataElement);
        }

        messageElement.appendChild(avatar);
        messageElement.appendChild(messageContent);

        this.messagesContainer.appendChild(messageElement);
        this.scrollToBottom();
    }

    // Set loading state
    setLoading(loading) {
        this.isLoading = loading;
        
        if (loading) {
            this.loadingOverlay.classList.add('show');
        } else {
            this.loadingOverlay.classList.remove('show');
        }
        
        this.updateSendButton();
    }

    // Scroll to bottom of messages
    scrollToBottom() {
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }

    // Sync knowledge base
    async syncKnowledge() {
        this.syncButton.disabled = true;
        this.syncButton.textContent = '🔄 Синхронизация...';

        try {
            const response = await fetch('/api/knowledge/sync', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    vaultPath: './obsidian-vault'
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const data = await response.json();
            
            // Show success message
            this.addMessage('assistant', `✅ Синхронизация завершена! Обработано файлов: ${data.filesProcessed}`, { 
                approach: 'knowledge_sync' 
            });
            
            // Update knowledge stats
            this.checkKnowledgeStatus();

        } catch (error) {
            console.error('Knowledge sync failed:', error);
            this.addMessage('assistant', `❌ Ошибка синхронизации: ${error.message}`, { 
                error: true 
            });
        } finally {
            this.syncButton.disabled = false;
            this.syncButton.textContent = '🔄 Синхронизировать';
        }
    }

    // Show knowledge panel
    showKnowledgePanel() {
        this.knowledgePanel.classList.add('show');
        this.checkKnowledgeStatus();
    }
}

// Initialize app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    window.jarvisApp = new JarvisApp();
    
    // Add keyboard shortcuts
    document.addEventListener('keydown', (e) => {
        // Ctrl+K to show knowledge panel
        if (e.ctrlKey && e.key === 'k') {
            e.preventDefault();
            window.jarvisApp.showKnowledgePanel();
        }
        
        // Escape to close knowledge panel
        if (e.key === 'Escape') {
            window.jarvisApp.knowledgePanel.classList.remove('show');
        }
    });
});

// Service worker for offline support (future enhancement)
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js')
            .then((registration) => {
                console.log('SW registered: ', registration);
            })
            .catch((registrationError) => {
                console.log('SW registration failed: ', registrationError);
            });
    });
}