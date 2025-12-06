import React, { useState, useRef, useEffect } from 'react';
import './App.css';
import { askQuestion, detectIntent, searchVector } from './services/api';

function App() {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [detectedIntents, setDetectedIntents] = useState([]);
  const [retrievedDocs, setRetrievedDocs] = useState([]);
  const [showDebugInfo, setShowDebugInfo] = useState(false);
  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const sendMessage = async (e) => {
    e.preventDefault();
    if (!input.trim() || loading) return;

    const userMessage = input.trim();
    setInput('');
    setError(null);
    setDetectedIntents([]);
    setRetrievedDocs([]);

    // Add user message
    const newUserMessage = {
      role: 'user',
      content: userMessage,
      timestamp: new Date()
    };
    setMessages(prev => [...prev, newUserMessage]);
    setLoading(true);

    try {
      // Option 1: Use new /ask endpoint (handles full flow internally)
      const answer = await askQuestion(userMessage, {
        table: 'dda_transactions',
        keyspace: 'transaction_keyspace',
        topK: 6,
        temperature: 0.3,
        maxTokens: 100
      });

      // Optionally: Show debug info (intent detection + vector search)
      if (showDebugInfo) {
        try {
          const intents = await detectIntent(userMessage);
          setDetectedIntents(intents);
          
          const searchResult = await searchVector(userMessage, intents, {
            table: 'dda_transactions',
            keyspace: 'transaction_keyspace',
            topK: 6
          });
          setRetrievedDocs(searchResult.documents || []);
        } catch (debugErr) {
          console.warn('Debug info fetch failed:', debugErr);
        }
      }

      const assistantMessage = {
        role: 'assistant',
        content: answer,
        timestamp: new Date()
      };

      setMessages(prev => [...prev, assistantMessage]);
    } catch (err) {
      console.error('Error sending message:', err);
      setError(err.response?.data?.message || err.message || 'Failed to get response');
      
      const errorMessage = {
        role: 'assistant',
        content: `Error: ${err.response?.data?.message || err.message || 'Failed to get response'}`,
        isError: true,
        timestamp: new Date()
      };
      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setLoading(false);
      inputRef.current?.focus();
    }
  };

  const clearChat = () => {
    setMessages([]);
    setError(null);
    inputRef.current?.focus();
  };

  return (
    <div className="app">
      <div className="chat-container">
        <div className="chat-header">
          <h1>🤖 RAG Chat - Data Platform Assistant</h1>
          <div className="header-actions">
            <label className="debug-toggle">
              <input 
                type="checkbox" 
                checked={showDebugInfo}
                onChange={(e) => setShowDebugInfo(e.target.checked)}
              />
              Show Debug Info
            </label>
            <button onClick={clearChat} className="clear-btn">Clear Chat</button>
          </div>
        </div>

        <div className="messages-container">
          {messages.length === 0 && (
            <div className="welcome-message">
              <h2>Welcome to RAG Chat!</h2>
              <p>Ask questions about your data platform:</p>
              <ul>
                <li>What is the schema of dda_transactions?</li>
                <li>Why was dda_transactions delayed yesterday?</li>
                <li>Which API reads from this table?</li>
                <li>What caused Kafka lag?</li>
              </ul>
            </div>
          )}

          {messages.map((msg, idx) => (
            <div key={idx} className={`message ${msg.role}`}>
              <div className="message-content">
                <div className="message-header">
                  <span className="message-role">
                    {msg.role === 'user' ? '👤 You' : '🤖 Assistant'}
                  </span>
                  {msg.mode && (
                    <span className="message-mode">Mode: {msg.mode}</span>
                  )}
                  {msg.confidence && (
                    <span className="message-confidence">
                      Confidence: {(msg.confidence * 100).toFixed(0)}%
                    </span>
                  )}
                </div>
                <div className={`message-text ${msg.isError ? 'error' : ''}`}>
                  {msg.content}
                </div>
                
                {msg.sources && msg.sources.length > 0 && (
                  <div className="message-sources">
                    <details>
                      <summary>📚 Sources ({msg.sources.length})</summary>
                      <div className="sources-list">
                        {msg.sources.map((source, sidx) => (
                          <div key={sidx} className="source-item">
                            <div className="source-header">
                              <span className="source-type">{source.sourceType}</span>
                              <span className="source-component">{source.component}</span>
                              {source.similarityScore && (
                                <span className="source-score">
                                  {(source.similarityScore * 100).toFixed(1)}% match
                                </span>
                              )}
                            </div>
                            <div className="source-content">{source.content}</div>
                          </div>
                        ))}
                      </div>
                    </details>
                  </div>
                )}

                {(msg.retrievalTime || msg.generationTime) && (
                  <div className="message-timing">
                    Retrieval: {msg.retrievalTime}ms | 
                    Generation: {msg.generationTime}ms
                  </div>
                )}
              </div>
            </div>
          ))}

          {/* Debug Info Panel */}
          {showDebugInfo && (detectedIntents.length > 0 || retrievedDocs.length > 0) && (
            <div className="debug-panel">
              <h3>🔍 Debug Information</h3>
              {detectedIntents.length > 0 && (
                <div className="debug-section">
                  <strong>Detected Document Types:</strong>
                  <div className="intent-tags">
                    {detectedIntents.map((intent, idx) => (
                      <span key={idx} className="intent-tag">{intent}</span>
                    ))}
                  </div>
                </div>
              )}
              {retrievedDocs.length > 0 && (
                <div className="debug-section">
                  <strong>Retrieved Documents ({retrievedDocs.length}):</strong>
                  <div className="docs-list">
                    {retrievedDocs.map((doc, idx) => (
                      <div key={idx} className="doc-item">
                        <span className="doc-type">{doc.sourceType}</span>
                        <span className="doc-component">{doc.component}</span>
                        {doc.similarityScore && (
                          <span className="doc-score">
                            {(doc.similarityScore * 100).toFixed(1)}%
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {loading && (
            <div className="message assistant">
              <div className="message-content">
                <div className="loading-indicator">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
                <div className="loading-text">Thinking...</div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {error && (
          <div className="error-banner">
            {error}
          </div>
        )}

        <form onSubmit={sendMessage} className="input-container">
          <input
            ref={inputRef}
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask a question about your data platform..."
            disabled={loading}
            className="chat-input"
            autoFocus
          />
          <button 
            type="submit" 
            disabled={loading || !input.trim()}
            className="send-button"
          >
            Send
          </button>
        </form>
      </div>
    </div>
  );
}

export default App;

