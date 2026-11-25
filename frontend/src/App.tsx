import { useEffect, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import './App.css'

type DocumentRecord = {
  id: number
  title: string
  sourceType: string
  sourceUri: string | null
}

type VersionRecord = {
  id: number
  documentId: number
  versionLabel: string
  current: boolean
}

type ChunkCitation = {
  chunkId: number
  chunkIndex: number
  pageNumber: number | null
  sourceUri: string | null
  documentTitle: string
  versionLabel: string
  metadata: Record<string, unknown>
}

type StitchedContext = {
  documentId: number
  versionId: number
  documentTitle: string
  versionLabel: string
  sourceUri: string | null
  startChunkIndex: number
  endChunkIndex: number
  content: string
  citations: ChunkCitation[]
}

type QueryAnswerResponse = {
  originalQuery: string
  normalizedQuery: string
  answer: string
  versionId: number
  topK: number
  citations: StitchedContext[]
}

type Message = {
  id: number
  role: 'user' | 'assistant'
  content: string
  citations?: StitchedContext[]
  isError?: boolean
}

async function readApiError(response: Response): Promise<string> {
  if (response.status === 503) {
    return 'The AI service is temporarily busy. Please try again in a moment.'
  }

  const body = await response.text()

  if (!body) {
    return `HTTP ${response.status}: ${response.statusText}`
  }

  try {
    const parsed = JSON.parse(body) as {
      detail?: string
      error?: string
      message?: string
    }
    return parsed.detail || parsed.message || parsed.error || body
  } catch {
    return body
  }
}

function App() {
  const [documents, setDocuments] = useState<DocumentRecord[]>([])
  const [versions, setVersions] = useState<VersionRecord[]>([])
  const [selectedDocumentId, setSelectedDocumentId] = useState<number | null>(null)
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)

  const [documentsLoading, setDocumentsLoading] = useState(true)
  const [versionsLoading, setVersionsLoading] = useState(false)
  const [backendError, setBackendError] = useState('')

  const [question, setQuestion] = useState('')
  const [messages, setMessages] = useState<Message[]>([])
  const [isSending, setIsSending] = useState(false)

  const selectedDocument = documents.find(
    (document) => document.id === selectedDocumentId,
  )

  const selectedVersion = versions.find(
    (version) => version.id === selectedVersionId,
  )

  // Load available documents from the Document Service.
  useEffect(() => {
    let active = true

    async function loadDocuments() {
      setDocumentsLoading(true)
      setBackendError('')

      try {
        const response = await fetch('/api/documents')

        if (!response.ok) {
          throw new Error(await readApiError(response))
        }

        const payload: unknown = await response.json()
        const list = Array.isArray(payload)
          ? payload
          : (payload as { value?: DocumentRecord[] }).value ?? []

        if (!active) return

        setDocuments(list as DocumentRecord[])

        if (list.length > 0) {
          setSelectedDocumentId((current) => current ?? list[0].id)
        } else {
          setBackendError('No documentation is currently available.')
        }
      } catch (error) {
        if (active) {
          setBackendError(
            error instanceof Error ? error.message : 'Unable to load documents.',
          )
        }
      } finally {
        if (active) setDocumentsLoading(false)
      }
    }

    void loadDocuments()

    return () => {
      active = false
    }
  }, [])

  // Load versions whenever the selected document changes.
  useEffect(() => {
    if (selectedDocumentId === null) {
      setVersions([])
      setSelectedVersionId(null)
      return
    }

    let active = true

    async function loadVersions() {
      setVersions([])
      setSelectedVersionId(null)
      setVersionsLoading(true)

      try {
        const response = await fetch(
          `/api/documents/${selectedDocumentId}/versions`,
        )

        if (!response.ok) {
          throw new Error(await readApiError(response))
        }

        const payload: unknown = await response.json()
        const list = Array.isArray(payload)
          ? payload
          : (payload as { value?: VersionRecord[] }).value ?? []

        if (!active) return

        const availableVersions = list as VersionRecord[]
        setVersions(availableVersions)

        const currentVersion = availableVersions.find(
          (version) => version.current,
        )

        setSelectedVersionId(
          currentVersion?.id ?? availableVersions[0]?.id ?? null,
        )

        if (availableVersions.length === 0) {
          setBackendError('This document does not have any available versions.')
        } else {
          setBackendError('')
        }
      } catch (error) {
        if (active) {
          setBackendError(
            error instanceof Error ? error.message : 'Unable to load versions.',
          )
        }
      } finally {
        if (active) setVersionsLoading(false)
      }
    }

    void loadVersions()

    return () => {
      active = false
    }
  }, [selectedDocumentId])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const trimmedQuestion = question.trim()

    if (
      !trimmedQuestion ||
      selectedDocumentId === null ||
      selectedVersionId === null ||
      isSending
    ) {
      return
    }

    const userMessage: Message = {
      id: Date.now(),
      role: 'user',
      content: trimmedQuestion,
    }

    setMessages((current) => [...current, userMessage])
    setQuestion('')
    setIsSending(true)

    try {
      const response = await fetch('/api/query/answer', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          query: trimmedQuestion,
          versionId: selectedVersionId,
          topK: 10,
        }),
      })

      if (!response.ok) {
        throw new Error(await readApiError(response))
      }

      const payload: unknown = await response.json()

      if (typeof payload !== 'object' || payload === null) {
        throw new Error('The Query API returned an invalid response.')
      }

      const result = payload as Partial<QueryAnswerResponse>

      if (typeof result.answer !== 'string' || result.answer.trim().length === 0) {
        throw new Error('The Query API returned an empty answer.')
      }

      const assistantMessage: Message = {
        id: Date.now() + 1,
        role: 'assistant',
        content: result.answer,
        citations: Array.isArray(result.citations) ? result.citations : [],
      }

      setMessages((current) => [...current, assistantMessage])
    } catch (error) {
      const errorMessage: Message = {
        id: Date.now() + 1,
        role: 'assistant',
        content:
          error instanceof Error
            ? error.message
            : 'An unexpected error occurred while contacting the Query API.',
        isError: true,
      }

      setMessages((current) => [...current, errorMessage])
    } finally {
      setIsSending(false)
    }
  }

  function handleNewConversation() {
    setMessages([])
    setQuestion('')
  }

  function handleComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
  }

  function handleSuggestionClick(suggestion: string) {
    setQuestion(suggestion)
  }

  const latestAnswer = [...messages]
    .reverse()
    .find((message) => message.role === 'assistant' && !message.isError)

  const displayedCitations =
    latestAnswer?.citations?.flatMap((context) =>
      (context.citations ?? []).map((citation) => ({
        citation,
        context,
      })),
    ) ?? []

  const backendConnected = !documentsLoading && !backendError && documents.length > 0

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

        <button
          className="new-chat-button"
          type="button"
          onClick={handleNewConversation}
        >
          <span>＋</span> New conversation
        </button>

        <div className="sidebar-section">
          <p className="section-label">DOCUMENTATION</p>

          {documentsLoading ? (
            <p className="sidebar-loading">Loading documentation...</p>
          ) : (
            documents.map((document) => (
              <button
                className={`document-item ${
                  selectedDocumentId === document.id ? 'active' : ''
                }`}
                type="button"
                key={document.id}
                onClick={() => setSelectedDocumentId(document.id)}
              >
                <span className="document-icon">
                  {document.title.slice(0, 2).toUpperCase()}
                </span>
                <span className="document-name">
                  <strong>{document.title}</strong>
                  <small>{document.sourceType} documentation</small>
                </span>
              </button>
            ))
          )}
        </div>

        <div className="sidebar-footer">
          <div className="connection-indicator">
            <span
              className={`status-dot ${
                backendConnected ? 'status-dot-connected' : ''
              }`}
            />
            <span>
              {documentsLoading
                ? 'Connecting to backend...'
                : backendConnected
                  ? 'Backend connected'
                  : 'Backend unavailable'}
            </span>
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
            <span
              className={`status-dot ${
                backendConnected ? 'status-dot-connected' : ''
              }`}
            />
            {backendConnected ? 'API connected' : 'API unavailable'}
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
                value={selectedDocumentId ?? ''}
                onChange={(event) =>
                  setSelectedDocumentId(
                    event.target.value ? Number(event.target.value) : null,
                  )
                }
                disabled={documentsLoading || documents.length === 0}
              >
                {documents.map((document) => (
                  <option key={document.id} value={document.id}>
                    {document.title}
                  </option>
                ))}
              </select>
            </div>

            <div className="selection-divider" />

            <div className="selection-field version-field">
              <label htmlFor="version-select">VERSION</label>
              <select
                id="version-select"
                value={selectedVersionId ?? ''}
                onChange={(event) =>
                  setSelectedVersionId(
                    event.target.value ? Number(event.target.value) : null,
                  )
                }
                disabled={versionsLoading || versions.length === 0}
              >
                {versions.map((version) => (
                  <option key={version.id} value={version.id}>
                    {version.versionLabel}
                    {version.current ? ' (Current)' : ''}
                  </option>
                ))}
              </select>
            </div>

            <span className="version-badge">
              {selectedVersion ? 'Version-aware' : 'No version'}
            </span>
          </div>

          {backendError && (
            <div className="api-error-banner" role="alert">
              {backendError}
            </div>
          )}

          {messages.length === 0 ? (
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
                    handleSuggestionClick(
                      'How does dependency injection work in Spring Boot?',
                    )
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
                    handleSuggestionClick(
                      'How do I configure application properties?',
                    )
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
                    handleSuggestionClick(
                      'What is the difference between @Component and @Service?',
                    )
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
                    handleSuggestionClick(
                      'What are the main changes between Spring Boot 3.5 and 4.2?',
                    )
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
          ) : (
            <div className="conversation-area" aria-live="polite">
              {messages.map((message) => (
                <div
                  className={`message-row ${
                    message.role === 'user' ? 'user-message' : 'system-message'
                  } ${message.isError ? 'error-message' : ''}`}
                  key={message.id}
                >
                  <div className="message-avatar">
                    {message.role === 'user' ? 'You' : 'D'}
                  </div>
                  <div className="message-content">
                    <strong>
                      {message.role === 'user' ? 'You' : 'DevDocs AI'}
                    </strong>
                    <p>{message.content}</p>
                  </div>
                </div>
              ))}

              {isSending && (
                <div className="message-row system-message">
                  <div className="message-avatar">D</div>
                  <div className="message-content">
                    <strong>DevDocs AI</strong>
                    <p className="loading-message">
                      Searching documentation and generating an answer...
                    </p>
                  </div>
                </div>
              )}
            </div>
          )}

          <div className="composer-area">
            <form className="question-composer" onSubmit={handleSubmit}>
              <textarea
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                onKeyDown={handleComposerKeyDown}
                placeholder="Ask a question about Java or Spring..."
                rows={3}
                aria-label="Your documentation question"
              />
              <div className="composer-footer">
                <span>Press Enter to send · Shift + Enter for a new line</span>
                <button
                  type="submit"
                  disabled={
                    !question.trim() ||
                    isSending ||
                    selectedVersionId === null ||
                    selectedDocumentId === null
                  }
                >
                  {isSending ? 'Thinking...' : 'Ask AI'} <span>↑</span>
                </button>
              </div>
            </form>
            <p className="composer-note">
              {selectedDocument && selectedVersion
                ? `Using ${selectedDocument.title} · Version ${selectedVersion.versionLabel}`
                : 'Select a document and version to begin.'}
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
          <span className="sources-count">{displayedCitations.length}</span>
        </div>

        {displayedCitations.length === 0 ? (
          <div className="sources-empty">
            <div className="sources-empty-icon">⌕</div>
            <strong>No sources yet</strong>
            <p>
              Retrieved documentation chunks and citations will appear here
              after a question is processed.
            </p>
          </div>
        ) : (
          <div className="sources-list">
            {displayedCitations.map(({ citation, context }, index) => (
              <article
                className="source-card"
                key={`${citation.chunkId}-${index}`}
              >
                <div className="source-card-heading">
                  <strong>{citation.documentTitle || context.documentTitle}</strong>
                  <span>Chunk #{citation.chunkIndex}</span>
                </div>

                <p className="source-version">
                  Version {citation.versionLabel || context.versionLabel}
                </p>

                {citation.pageNumber !== null &&
                  citation.pageNumber !== undefined && (
                    <p className="source-page">Page {citation.pageNumber}</p>
                  )}

                <p className="source-chunk-id">
                  Chunk ID: {citation.chunkId}
                </p>

                {citation.sourceUri && (
                  <a
                    className="source-link"
                    href={citation.sourceUri}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Open source ↗
                  </a>
                )}
              </article>
            ))}
          </div>
        )}

        <div className="context-card">
          <span className="context-card-icon">ⓘ</span>
          <div>
            <strong>Version-aware retrieval</strong>
            <p>
              Answers and citations are returned by the backend for the
              selected documentation version.
            </p>
          </div>
        </div>

        <div className="sources-footer">
          <span
            className={`status-dot ${
              backendConnected ? 'status-dot-connected' : ''
            }`}
          />
          {backendConnected ? 'Connected to Query API' : 'Waiting for API'}
        </div>
      </aside>
    </div>
  )
}

export default App
