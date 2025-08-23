// Jarvis AI Assistant - Frontend Application
class JarvisApp {
    constructor() {
        this.sessionId = this.generateSessionId();
        this.isOnline = false;
        this.isLoading = false;
        this.currentTab = 'chat';
        this.logsPaused = false;
        this.logsEventSource = null;
        this.thinkingEventSource = null;
        this.currentThinkingElement = null;
        
        this.initializeElements();
        this.bindEvents();
        this.switchTab('chat'); // Initialize with chat tab
        this.checkStatus();
        this.updateSessionDisplay();
        this.loadVersion();
        this.initializeAIAvatar();
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

    // Initialize AI Avatar
    initializeAIAvatar() {
        const canvas = document.getElementById('ai-avatar');
        if (canvas) {
            this.aiAvatar = new AIAvatar(canvas);
            console.log('AI Avatar initialized');
        } else {
            console.warn('AI Avatar canvas not found');
        }
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

    // Connect to thinking stream for real-time thoughts
    connectThinkingStream() {
        if (this.thinkingEventSource) {
            this.thinkingEventSource.close();
        }
        
        console.log('Connecting to thinking stream for session:', this.sessionId);
        
        this.thinkingEventSource = new EventSource(`/api/thinking/stream/${this.sessionId}`);
        
        this.thinkingEventSource.onopen = () => {
            console.log('Connected to thinking stream');
        };
        
        this.thinkingEventSource.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                this.handleThinkingEvent(data);
            } catch (e) {
                console.error('Error parsing thinking event:', e);
            }
        };
        
        this.thinkingEventSource.onerror = (error) => {
            console.error('Thinking stream error:', error);
        };
    }
    
    // Handle thinking events from SSE
    handleThinkingEvent(data) {
        console.log('Thinking event:', data);
        
        if (data.type === 'connected') {
            console.log('Connected to Jarvis thinking stream');
            return;
        }
        
        if (data.type === 'complete') {
            this.finishThinking();
            return;
        }
        
        // Show thinking message in real-time
        this.addThinkingStep(data.message, data.type);
    }
    
    // Add thinking step to chat
    addThinkingStep(message, type) {
        if (!this.currentThinkingElement) {
            this.createThinkingElement();
        }
        
        const stepElement = document.createElement('div');
        stepElement.className = `thinking-step thinking-${type}`;
        stepElement.textContent = message;
        
        const stepsContainer = this.currentThinkingElement.querySelector('.thinking-steps');
        stepsContainer.appendChild(stepElement);
        
        this.scrollToBottom();
    }
    
    // Create thinking container
    createThinkingElement() {
        const messageElement = document.createElement('div');
        messageElement.className = 'message assistant thinking-message';
        
        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = '🤖';
        
        const messageContent = document.createElement('div');
        messageContent.className = 'message-content';
        messageContent.innerHTML = `
            <div class="thinking-header">
                <span class="thinking-title">🧠 Джарвис думает...</span>
            </div>
            <div class="thinking-steps"></div>
        `;
        
        messageElement.appendChild(avatar);
        messageElement.appendChild(messageContent);
        
        this.messagesContainer.appendChild(messageElement);
        this.currentThinkingElement = messageElement;
        
        this.scrollToBottom();
    }
    
    // Finish thinking and prepare for response
    finishThinking() {
        if (this.currentThinkingElement) {
            const header = this.currentThinkingElement.querySelector('.thinking-title');
            if (header) {
                header.textContent = '✅ Готов ответить';
            }
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

        // Connect to thinking stream for real-time updates
        this.connectThinkingStream();

        // Set avatar to thinking mode
        if (this.aiAvatar) {
            this.aiAvatar.setMode('thinking');
        }

        // Real-time thinking display instead of loader

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
            
            // Set avatar to speaking mode
            if (this.aiAvatar) {
                this.aiAvatar.setMode('speaking');
            }
            
            // Add assistant response to chat
            this.addMessage('assistant', data.response, data.metadata);

        } catch (error) {
            console.error('Send message failed:', error);
            this.addMessage('assistant', `Извините, произошла ошибка: ${error.message}`, { error: true });
        } finally {
            // Loader management removed - using real-time thinking display instead
            
            // Close thinking stream and reset
            if (this.thinkingEventSource) {
                this.thinkingEventSource.close();
                this.thinkingEventSource = null;
            }
            this.currentThinkingElement = null;
            
            // Reset avatar to idle mode
            if (this.aiAvatar) {
                this.aiAvatar.setMode('idle');
            }
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

        // Add reasoning details if available
        if (metadata && metadata.reasoning_details) {
            const reasoningElement = this.createReasoningElement(metadata.reasoning_details);
            messageContent.appendChild(reasoningElement);
        }

        // Add metadata if available
        if (metadata) {
            const metadataElement = document.createElement('div');
            metadataElement.className = 'message-metadata';
            
            const metadataText = [];
            if (metadata.approach) {
                metadataText.push(`Подход: ${metadata.approach}`);
            }
            if (metadata.reasoning_steps) {
                metadataText.push(`🧠 Reasoning: ${metadata.reasoning_steps} шагов`);
            }
            if (metadata.tools_used && metadata.tools_used.length > 0) {
                metadataText.push(`🔧 Инструменты: ${metadata.tools_used.join(', ')}`);
            }
            if (metadata.steps_count) {
                metadataText.push(`🎯 Smart Conductor: ${metadata.steps_count} шагов`);
            }
            if (metadata.error) {
                metadataText.push('⚠️ Ошибка');
                messageContent.style.borderColor = 'var(--error)';
                messageContent.style.backgroundColor = 'rgba(239, 68, 68, 0.1)';
            }
            
            metadataElement.textContent = metadataText.join(' • ');
            messageContent.appendChild(metadataElement);
            
            // Show detailed execution steps for Smart Conductor
            if (metadata.execution_details && metadata.execution_details.length > 0) {
                const stepsElement = document.createElement('details');
                stepsElement.className = 'reasoning-steps';
                stepsElement.innerHTML = `
                    <summary>🔍 Детали выполнения (${metadata.execution_details.length} шагов)</summary>
                    <div class="steps-content">
                        ${metadata.execution_details.map(step => `
                            <div class="step-item">
                                <div class="step-header">
                                    <span class="step-number">Шаг ${step.step}</span>
                                    <span class="step-type">${step.type}</span>
                                </div>
                                <div class="step-action">${step.action}</div>
                                ${step.result ? `<div class="step-result">${step.result}...</div>` : ''}
                            </div>
                        `).join('')}
                    </div>
                `;
                messageContent.appendChild(stepsElement);
            }
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

    // Create reasoning element for displaying thinking process
    createReasoningElement(reasoningDetails) {
        const container = document.createElement('div');
        container.className = 'reasoning-container';
        container.style.cssText = `
            background: rgba(0, 255, 136, 0.05);
            border: 1px solid rgba(0, 255, 136, 0.2);
            border-radius: 8px;
            padding: 12px;
            margin: 12px 0;
            font-size: 0.9em;
        `;
        
        const header = document.createElement('div');
        header.style.cssText = `
            font-weight: bold;
            color: var(--primary);
            margin-bottom: 8px;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 8px;
        `;
        header.innerHTML = '🧠 <span>Процесс reasoning (нажмите для деталей)</span>';
        
        const details = document.createElement('div');
        details.className = 'reasoning-details';
        details.style.cssText = `
            display: none;
            margin-top: 12px;
        `;
        
        // Create steps
        reasoningDetails.forEach((step, index) => {
            const stepElement = document.createElement('div');
            stepElement.style.cssText = `
                background: rgba(0, 0, 0, 0.3);
                border-left: 3px solid var(--primary);
                padding: 8px 12px;
                margin: 8px 0;
                border-radius: 4px;
            `;
            
            const stepHeader = document.createElement('div');
            stepHeader.style.cssText = `
                font-weight: bold;
                color: var(--primary);
                margin-bottom: 6px;
            `;
            stepHeader.textContent = `Шаг ${step.step}`;
            
            const thought = document.createElement('div');
            thought.style.cssText = 'margin: 4px 0; opacity: 0.9;';
            thought.innerHTML = `💭 <strong>Мысль:</strong> ${step.thought}`;
            
            stepElement.appendChild(stepHeader);
            stepElement.appendChild(thought);
            
            if (step.action) {
                const action = document.createElement('div');
                action.style.cssText = 'margin: 4px 0; opacity: 0.9;';
                action.innerHTML = `🎯 <strong>Действие:</strong> ${step.action}`;
                stepElement.appendChild(action);
            }
            
            if (step.input) {
                const input = document.createElement('div');
                input.style.cssText = 'margin: 4px 0; opacity: 0.9;';
                input.innerHTML = `📝 <strong>Вход:</strong> ${step.input}`;
                stepElement.appendChild(input);
            }
            
            if (step.observation) {
                const observation = document.createElement('div');
                observation.style.cssText = 'margin: 4px 0; opacity: 0.9;';
                observation.innerHTML = `👁️ <strong>Наблюдение:</strong> ${step.observation}`;
                stepElement.appendChild(observation);
            }
            
            details.appendChild(stepElement);
        });
        
        // Toggle details on click
        header.addEventListener('click', () => {
            const isVisible = details.style.display !== 'none';
            details.style.display = isVisible ? 'none' : 'block';
            header.querySelector('span').textContent = isVisible ? 
                'Процесс reasoning (нажмите для деталей)' : 
                'Процесс reasoning (нажмите чтобы скрыть)';
        });
        
        container.appendChild(header);
        container.appendChild(details);
        
        return container;
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
    
    // Connect to thinking stream for real-time updates
    connectThinkingStream() {
        if (this.thinkingEventSource) {
            this.thinkingEventSource.close();
        }
        
        const url = `/api/thinking/stream/${this.sessionId}`;
        console.log('Connecting to thinking stream:', url);
        
        this.thinkingEventSource = new EventSource(url);
        
        this.thinkingEventSource.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                this.handleThinkingEvent(data);
            } catch (error) {
                console.error('Failed to parse thinking event:', error);
            }
        };
        
        this.thinkingEventSource.onerror = (event) => {
            console.error('Thinking stream error:', event);
        };
        
        this.thinkingEventSource.onopen = (event) => {
            console.log('Thinking stream connected');
        };
    }
    
    // Handle thinking events from SSE
    handleThinkingEvent(data) {
        console.log('Thinking event:', data);
        
        if (data.type === 'connected') {
            // Stream connected
            return;
        }
        
        if (data.type === 'complete') {
            // Thinking complete - keep display but mark as finished
            if (this.currentThinkingElement) {
                this.currentThinkingElement.classList.add('thinking-finished');
                this.currentThinkingElement = null; // Reset for next request
            }
            return;
        }
        
        // Create or update thinking display
        if (!this.currentThinkingElement) {
            this.currentThinkingElement = this.createThinkingElement();
            this.messagesContainer.appendChild(this.currentThinkingElement);
            this.scrollToBottom();
        }
        
        // Add thinking step
        this.addThinkingStep(data);
    }
    
    // Create thinking element container
    createThinkingElement() {
        const element = document.createElement('div');
        element.className = 'message assistant thinking-message';
        element.innerHTML = `
            <div class="message-avatar">
                <div class="avatar-circle">J</div>
            </div>
            <div class="message-content">
                <div class="message-text">
                    <div class="thinking-header">
                        <div class="thinking-title">🧠 Мысли Джарвиса</div>
                    </div>
                    <div class="thinking-steps"></div>
                </div>
            </div>
        `;
        return element;
    }
    
    // Add thinking step to display
    addThinkingStep(data) {
        if (!this.currentThinkingElement) return;
        
        const stepsContainer = this.currentThinkingElement.querySelector('.thinking-steps');
        if (!stepsContainer) return;
        
        const stepElement = document.createElement('div');
        stepElement.className = `thinking-step thinking-${data.type}`;
        stepElement.textContent = data.message;
        
        stepsContainer.appendChild(stepElement);
        this.scrollToBottom();
        
        // Add animation
        setTimeout(() => {
            stepElement.style.opacity = '1';
            stepElement.style.transform = 'translateX(0)';
        }, 50);
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

// AI Avatar Animation
class AIAvatar {
    constructor(canvas) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.pixelSize = 5;
        this.width = canvas.width / this.pixelSize;
        this.height = canvas.height / this.pixelSize;
        
        // Avatar state
        this.time = 0;
        this.mode = 'idle'; // idle, thinking, speaking
        this.brainActivity = [];
        this.particles = [];
        
        // Initialize brain activity matrix
        for (let i = 0; i < this.width; i++) {
            this.brainActivity[i] = [];
            for (let j = 0; j < this.height; j++) {
                this.brainActivity[i][j] = Math.random() * 0.3;
            }
        }
        
        // Avatar colors
        this.colors = {
            background: '#0a0a0a',
            base: '#00ff88',
            accent: '#00ffaa',
            glow: '#00ff8844',
            particle: '#00ffcc'
        };
        
        this.animate();
    }
    
    drawPixel(x, y, color) {
        this.ctx.fillStyle = color;
        this.ctx.fillRect(
            x * this.pixelSize,
            y * this.pixelSize,
            this.pixelSize,
            this.pixelSize
        );
    }
    
    drawHead() {
        const centerX = this.width / 2;
        const centerY = this.height / 2;
        const radius = this.width / 3;
        
        // Clear canvas
        this.ctx.fillStyle = this.colors.background;
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
        
        // Draw pixel head shape
        for (let x = 0; x < this.width; x++) {
            for (let y = 0; y < this.height; y++) {
                const dx = x - centerX;
                const dy = y - centerY;
                const distance = Math.sqrt(dx * dx + dy * dy);
                
                // Head outline
                if (distance < radius && distance > radius - 2) {
                    this.drawPixel(x, y, this.colors.base);
                }
                
                // Brain pattern
                if (distance < radius - 2) {
                    const activity = this.brainActivity[x][y];
                    const pulse = Math.sin(this.time * 0.05 + distance * 0.3) * 0.5 + 0.5;
                    const intensity = activity * pulse;
                    
                    if (intensity > 0.3) {
                        const alpha = Math.floor(intensity * 255).toString(16).padStart(2, '0');
                        this.drawPixel(x, y, this.colors.accent + alpha);
                    }
                }
                
                // Eyes
                const eyeY = centerY - 3;
                const leftEyeX = centerX - 5;
                const rightEyeX = centerX + 5;
                
                if ((Math.abs(x - leftEyeX) < 2 && Math.abs(y - eyeY) < 2) ||
                    (Math.abs(x - rightEyeX) < 2 && Math.abs(y - eyeY) < 2)) {
                    const blink = Math.random() > 0.995 ? 0 : 1;
                    if (blink) {
                        this.drawPixel(x, y, this.colors.base);
                    }
                }
                
                // Mouth animation (when speaking)
                if (this.mode === 'speaking') {
                    const mouthY = centerY + 5;
                    const mouthWidth = 4 + Math.sin(this.time * 0.2) * 2;
                    if (Math.abs(x - centerX) < mouthWidth && Math.abs(y - mouthY) < 1) {
                        this.drawPixel(x, y, this.colors.accent);
                    }
                }
            }
        }
        
        // Update brain activity
        if (Math.random() > 0.9) {
            const x = Math.floor(Math.random() * this.width);
            const y = Math.floor(Math.random() * this.height);
            this.brainActivity[x][y] = Math.min(1, this.brainActivity[x][y] + 0.5);
        }
        
        // Decay brain activity
        for (let x = 0; x < this.width; x++) {
            for (let y = 0; y < this.height; y++) {
                this.brainActivity[x][y] *= 0.98;
            }
        }
        
        // Draw particles
        this.updateParticles();
    }
    
    updateParticles() {
        // Add new particles occasionally
        if (Math.random() > 0.95) {
            this.particles.push({
                x: Math.random() * this.width,
                y: this.height,
                vx: (Math.random() - 0.5) * 0.5,
                vy: -Math.random() * 0.5 - 0.5,
                life: 1
            });
        }
        
        // Update and draw particles
        this.particles = this.particles.filter(p => {
            p.x += p.vx;
            p.y += p.vy;
            p.life -= 0.02;
            
            if (p.life > 0) {
                const alpha = Math.floor(p.life * 255).toString(16).padStart(2, '0');
                this.drawPixel(Math.floor(p.x), Math.floor(p.y), this.colors.particle + alpha);
                return true;
            }
            return false;
        });
    }
    
    setMode(mode) {
        this.mode = mode;
        const statusText = document.getElementById('avatar-status-text');
        if (statusText) {
            switch(mode) {
                case 'thinking':
                    statusText.textContent = 'Обрабатываю...';
                    break;
                case 'speaking':
                    statusText.textContent = 'Отвечаю...';
                    break;
                default:
                    statusText.textContent = 'Готов к работе';
            }
        }
    }
    
    animate() {
        this.time++;
        this.drawHead();
        requestAnimationFrame(() => this.animate());
    }
}

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