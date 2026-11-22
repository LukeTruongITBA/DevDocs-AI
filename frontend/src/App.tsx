import { useState } from 'react'
import './App.css'

function App() {
  const [selectedDocument, setSelectedDocument] = useState('spring-boot')
  const [selectedVersion, setSelectedVersion] = useState('4.2.0')
  const [question, setQuestion] = useState('')

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
  }

  return (
      <div className="app-shell">
        <aside className="sidebar">
          <div className="brand">
            <div className="brand-icon">D</div>
            <div>
              <h1>DevDocs AI</h1>
              <span>Developer documentation</span>
            </div>
          </div>

          <button className="new-chat-button" type="button">
            <span>＋</span> New conversation
          </button>

          <div className="sidebar-section">
            <p className="section-label">DOCUMENTATION</p>

            <button
                className={`document-item ${selectedDocument === 'spring-boot' ? 'active' : ''}`}
                type="button"
                onClick={() => setSelectedDocument('spring-boot')}
            >
              <span className="document-icon">SB</span>
              <span className="document-name">
              <strong>Spring Boot</strong>
              <small>Reference documentation</small>
            </span>
            </button>

            <button
                className={`document-item ${selectedDocument === 'spring-framework' ? 'active' : ''}`}
                type="button"
                onClick={() => setSelectedDocument('spring-framework')}
            >
              <span className="document-icon framework-icon">SF</span>
              <span className="document-name">
              <strong>Spring Framework</strong>
              <small>Reference documentation</small>
            </span>
            </button>

            <button
                className={`document-item ${selectedDocument === 'java' ? 'active' : ''}`}
                type="button"
                onClick={() => setSelectedDocument('java')}
            >
              <span className="document-icon java-icon">J</span>
              <span className="document-name">
              <strong>Java Documentation</strong>
              <small>Language reference</small>
            </span>
            </button>
          </div>

          <div className="sidebar-footer">
            <div className="connection-indicator">
              <span className="status-dot" />
              <span>Backend not connected</span>
            </div>
            <p>DevDocs AI · MVP</p>
          </div>
        </aside>

        <main className="main-content">
          <header className="topbar">
            <div>
              <span className="breadcrumb">Workspace</span>
              <span className="breadcrumb-separator">/</span>
              <strong>Documentation Assistant</strong>
            </div>

            <div className="topbar-status">
              <span className="status-dot" />
              API integration pending
            </div>
          </header>

          <section className="chat-workspace">
            <div className="workspace-heading">
              <div className="eyebrow">VERSION-AWARE RAG ASSISTANT</div>
              <h2>Ask your documentation.</h2>
              <p>
                Get answers grounded in Java and Spring documentation,
                with version-aware context and source citations.
              </p>
            </div>

            <div className="selection-panel">
              <div className="selection-field">
                <label htmlFor="document-select">DOCUMENTATION</label>
                <select
                    id="document-select"
                    value={selectedDocument}
                    onChange={(event) => setSelectedDocument(event.target.value)}
                >
                  <option value="spring-boot">Spring Boot</option>
                  <option value="spring-framework">Spring Framework</option>
                  <option value="java">Java Documentation</option>
                </select>
              </div>

              <div className="selection-divider" />

              <div className="selection-field version-field">
                <label htmlFor="version-select">VERSION</label>
                <select
                    id="version-select"
                    value={selectedVersion}
                    onChange={(event) => setSelectedVersion(event.target.value)}
                >
                  <option value="4.2.0">4.2.0</option>
                  <option value="3.6">3.6</option>
                  <option value="3.5">3.5</option>
                </select>
              </div>

              <span className="version-badge">Version-aware</span>
            </div>

            <div className="welcome-area">
              <div className="welcome-icon">✳</div>
              <h3>What would you like to learn?</h3>
              <p>
                Ask a technical question and explore answers based on
                your selected documentation version.
              </p>

              <div className="suggestion-grid">
                <button
                    type="button"
                    onClick={() =>
                        setQuestion('How does dependency injection work in Spring Boot?')
                    }
                >
                  <span className="suggestion-icon">⌘</span>
                  <span>
                  <strong>Dependency injection</strong>
                  <small>Understand Spring IoC and beans</small>
                </span>
                  <span className="suggestion-arrow">↗</span>
                </button>

                <button
                    type="button"
                    onClick={() =>
                        setQuestion('How do I configure application properties?')
                    }
                >
                  <span className="suggestion-icon">⚙</span>
                  <span>
                  <strong>Application configuration</strong>
                  <small>Explore properties and YAML</small>
                </span>
                  <span className="suggestion-arrow">↗</span>
                </button>

                <button
                    type="button"
                    onClick={() =>
                        setQuestion('What is the difference between @Component and @Service?')
                    }
                >
                  <span className="suggestion-icon">◇</span>
                  <span>
                  <strong>Spring annotations</strong>
                  <small>Learn common annotations</small>
                </span>
                  <span className="suggestion-arrow">↗</span>
                </button>

                <button
                    type="button"
                    onClick={() =>
                        setQuestion('What are the main changes between Spring Boot 3.5 and 4.2?')
                    }
                >
                  <span className="suggestion-icon">⇄</span>
                  <span>
                  <strong>Version comparison</strong>
                  <small>Compare documentation versions</small>
                </span>
                  <span className="suggestion-arrow">↗</span>
                </button>
              </div>
            </div>

            <div className="composer-area">
              <form className="question-composer" onSubmit={handleSubmit}>
              <textarea
                  value={question}
                  onChange={(event) => setQuestion(event.target.value)}
                  placeholder="Ask a question about Java or Spring..."
                  rows={3}
                  aria-label="Your documentation question"
              />
                <div className="composer-footer">
                  <span>Answers will include source citations when API integration is ready.</span>
                  <button type="submit" disabled>
                    Ask AI <span>↑</span>
                  </button>
                </div>
              </form>
              <p className="composer-note">
                Query API is not connected yet. Questions are not sent to a backend.
              </p>
            </div>
          </section>
        </main>

        <aside className="sources-panel">
          <div className="sources-heading">
            <div>
              <span className="eyebrow">RETRIEVAL CONTEXT</span>
              <h3>Sources</h3>
            </div>
            <span className="sources-count">0</span>
          </div>

          <div className="sources-empty">
            <div className="sources-empty-icon">⌕</div>
            <strong>No sources yet</strong>
            <p>
              Retrieved documentation chunks and citations will appear here
              after a question is processed.
            </p>
          </div>

          <div className="context-card">
            <span className="context-card-icon">ⓘ</span>
            <div>
              <strong>Version-aware retrieval</strong>
              <p>
                The assistant is designed to keep answers grounded in
                the selected documentation version.
              </p>
            </div>
          </div>

          <div className="sources-footer">
            <span className="status-dot" />
            Waiting for Query API
          </div>
        </aside>
      </div>
  )
}

export default App