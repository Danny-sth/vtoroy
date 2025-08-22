// Jarvis AI Assistant - Frontend Application
class JarvisApp {
    constructor() {
        this.sessionId = this.generateSessionId();
        this.isOnline = false;
        this.isLoading = false;
        this.currentTab = 'chat';
        this.logsPaused = false;
        this.logsEventSource = null;
        
        this.initializeElements();
        this.bindEvents();
        this.switchTab('chat'); // Initialize with chat tab
        this.checkStatus();
        this.updateSessionDisplay();
        this.loadVersion();
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
        
        // Tab elements
        this.tabs = document.querySelectorAll('.tab');
        this.tabPanels = document.querySelectorAll('.tab-panel');
        
        // Knowledge tab elements
        this.syncButtonLarge = document.getElementById('sync-button-large');
        this.knowledgeStatsLarge = document.getElementById('knowledge-stats-large');
        
        // Logs elements
        this.logsContainer = document.getElementById('logs-container');
        this.logsPauseBtn = document.getElementById('logs-pause');
        this.logsClearBtn = document.getElementById('logs-clear');
        this.logsDownloadBtn = document.getElementById('logs-download');
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
        
        // Tab navigation
        this.tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const tabName = tab.getAttribute('data-tab');
                this.switchTab(tabName);
            });
        });
        
        // Knowledge panel events
        if (this.knowledgeClose) {
            this.knowledgeClose.addEventListener('click', () => {
                this.knowledgePanel.classList.remove('show');
            });
        }
        
        if (this.syncButton) {
            this.syncButton.addEventListener('click', () => this.syncKnowledge());
        }
        
        if (this.syncButtonLarge) {
            this.syncButtonLarge.addEventListener('click', () => this.syncKnowledge());
        }
        
        // Logs controls
        if (this.logsPauseBtn) {
            this.logsPauseBtn.addEventListener('click', () => this.toggleLogsPause());
        }
        
        if (this.logsClearBtn) {
            this.logsClearBtn.addEventListener('click', () => this.clearLogs());
        }
        
        if (this.logsDownloadBtn) {
            this.logsDownloadBtn.addEventListener('click', () => this.downloadLogs());
        }
        
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
            const syncTime = data.lastSync ? new Date(data.lastSync).toLocaleString('ru') : 'Никогда';
            lastSync.textContent = syncTime;
        }
        
        // Update large panel
        const documentsCountLarge = document.getElementById('documents-count-large');
        const lastSyncLarge = document.getElementById('last-sync-large');
        const vectorsCount = document.getElementById('vectors-count');
        
        if (documentsCountLarge) {
            documentsCountLarge.textContent = data.totalFiles || '0';
        }
        
        if (lastSyncLarge) {
            const syncTime = data.lastSync ? 
                new Date(data.lastSync).toLocaleDateString('ru') : 'Никогда';
            lastSyncLarge.textContent = syncTime;
        }
        
        if (vectorsCount) {
            vectorsCount.textContent = data.totalFiles || '0';
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
        
        // Preserve formatting for long responses
        const contentWithLineBreaks = content.replace(/\n/g, '<br>');
        messageContent.innerHTML = contentWithLineBreaks;

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
        // Immediate scroll
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
        // Delayed scroll to ensure DOM is updated
        setTimeout(() => {
            this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
        }, 50);
    }

    // Sync knowledge base
    async syncKnowledge() {
        // Update both buttons
        const buttons = [this.syncButton, this.syncButtonLarge].filter(Boolean);
        buttons.forEach(btn => {
            btn.disabled = true;
            btn.innerHTML = '<span class="button-icon">🔄</span><span>Синхронизация...</span>';
        });

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
            
            // Show success message only on chat tab
            if (this.currentTab === 'chat') {
                this.addMessage('assistant', `✅ Синхронизация завершена! Обработано файлов: ${data.filesProcessed}`, { 
                    approach: 'knowledge_sync' 
                });
            }
            
            // Update knowledge stats
            this.checkKnowledgeStatus();

        } catch (error) {
            console.error('Knowledge sync failed:', error);
            if (this.currentTab === 'chat') {
                this.addMessage('assistant', `❌ Ошибка синхронизации: ${error.message}`, { 
                    error: true 
                });
            }
        } finally {
            buttons.forEach(btn => {
                btn.disabled = false;
                if (btn === this.syncButton) {
                    btn.textContent = '🔄 Синхронизировать';
                } else {
                    btn.innerHTML = '<span class="button-icon">🔄</span><span>Синхронизировать Obsidian</span>';
                }
            });
        }
    }

    // Show knowledge panel
    showKnowledgePanel() {
        this.knowledgePanel.classList.add('show');
        this.checkKnowledgeStatus();
    }
    
    // Switch tabs
    switchTab(tabName) {
        this.currentTab = tabName;
        
        // Update tab buttons
        this.tabs.forEach(tab => {
            if (tab.getAttribute('data-tab') === tabName) {
                tab.classList.add('active');
            } else {
                tab.classList.remove('active');
            }
        });
        
        // Update tab panels
        this.tabPanels.forEach(panel => {
            if (panel.id === `${tabName}-panel` || 
                (tabName === 'knowledge' && panel.id === 'knowledge-panel-content')) {
                panel.classList.add('active');
            } else {
                panel.classList.remove('active');
            }
        });
        
        // Initialize tab content
        if (tabName === 'knowledge') {
            this.checkKnowledgeStatus();
        } else if (tabName === 'logs') {
            this.initLogs();
        }
    }
    
    // Initialize logs functionality
    initLogs() {
        if (this.logsEventSource) {
            return; // Already initialized
        }
        
        this.connectToLogStream();
        this.loadRecentLogs();
    }
    
    // Connect to real-time log stream
    connectToLogStream() {
        if (!this.isOnline) {
            setTimeout(() => this.connectToLogStream(), 5000);
            return;
        }
        
        try {
            this.logsEventSource = new EventSource('/api/system/logs/stream');
            
            this.logsEventSource.onopen = () => {
                console.log('Connected to log stream');
            };
            
            this.logsEventSource.addEventListener('connected', (event) => {
                const data = JSON.parse(event.data);
                this.clearConnectingMessage();
                this.addLogEntry(data);
            });
            
            this.logsEventSource.addEventListener('log', (event) => {
                if (!this.logsPaused) {
                    const data = JSON.parse(event.data);
                    this.addLogEntry(data);
                }
            });
            
            this.logsEventSource.onerror = (error) => {
                console.error('Log stream error:', error);
                if (this.logsEventSource.readyState === EventSource.CLOSED) {
                    setTimeout(() => {
                        if (this.currentTab === 'logs') {
                            this.connectToLogStream();
                        }
                    }, 5000);
                }
            };
        } catch (error) {
            console.error('Failed to connect to log stream:', error);
        }
    }
    
    // Load recent logs
    async loadRecentLogs() {
        try {
            const response = await fetch('/api/system/logs/recent?lines=50');
            const data = await response.json();
            
            if (data.logs && data.logs.length > 0) {
                this.clearConnectingMessage();
                data.logs.forEach(log => this.addLogEntry(log));
            }
        } catch (error) {
            console.error('Failed to load recent logs:', error);
        }
    }
    
    // Clear connecting message
    clearConnectingMessage() {
        const connectingEntry = this.logsContainer.querySelector('.connecting');
        if (connectingEntry) {
            connectingEntry.remove();
        }
    }
    
    // Add log entry to container
    addLogEntry(logData) {
        const logEntry = document.createElement('div');
        logEntry.className = 'log-entry';
        
        const timestamp = document.createElement('span');
        timestamp.className = 'log-timestamp';
        timestamp.textContent = logData.timestamp || new Date().toLocaleTimeString('ru');
        
        const level = document.createElement('span');
        level.className = `log-level ${(logData.level || 'INFO').toLowerCase()}`;
        level.textContent = logData.level || 'INFO';
        
        const message = document.createElement('span');
        message.className = 'log-message';
        message.textContent = logData.message || '';
        
        logEntry.appendChild(timestamp);
        logEntry.appendChild(level);
        logEntry.appendChild(message);
        
        this.logsContainer.appendChild(logEntry);
        
        // Auto-scroll to bottom
        this.logsContainer.scrollTop = this.logsContainer.scrollHeight;
        
        // Limit number of log entries (keep last 200)
        const entries = this.logsContainer.querySelectorAll('.log-entry');
        if (entries.length > 200) {
            entries[0].remove();
        }
    }
    
    // Toggle logs pause
    toggleLogsPause() {
        this.logsPaused = !this.logsPaused;
        
        const pauseBtn = this.logsPauseBtn;
        if (this.logsPaused) {
            pauseBtn.innerHTML = '<span class="button-icon">▶️</span><span>Продолжить</span>';
        } else {
            pauseBtn.innerHTML = '<span class="button-icon">⏸️</span><span>Пауза</span>';
        }
    }
    
    // Clear logs
    clearLogs() {
        const logEntries = this.logsContainer.querySelectorAll('.log-entry:not(.connecting)');
        logEntries.forEach(entry => entry.remove());
    }
    
    // Download logs
    downloadLogs() {
        const logs = Array.from(this.logsContainer.querySelectorAll('.log-entry:not(.connecting)'))
            .map(entry => {
                const timestamp = entry.querySelector('.log-timestamp').textContent;
                const level = entry.querySelector('.log-level').textContent;
                const message = entry.querySelector('.log-message').textContent;
                return `[${timestamp}] ${level}: ${message}`;
            })
            .join('\n');
        
        const blob = new Blob([logs], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `jarvis-logs-${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.txt`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }
    
    // Load version information from API
    async loadVersion() {
        try {
            const response = await fetch('/api/system/version');
            if (response.ok) {
                const data = await response.json();
                const versionElement = document.querySelector('.version');
                if (versionElement && data.version) {
                    versionElement.textContent = `v${data.version}`;
                }
            }
        } catch (error) {
            console.warn('Failed to load version info:', error);
            // Version element will keep its default value
        }
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