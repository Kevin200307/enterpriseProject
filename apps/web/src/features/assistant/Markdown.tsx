import ReactMarkdown from 'react-markdown'

/**
 * Assistant output is untrusted. react-markdown never renders raw HTML (no rehype-raw), and its default URL
 * transform drops javascript: links; we also force safe link attributes.
 */
export default function Markdown({ children }: { children: string }) {
  return (
    <ReactMarkdown
      components={{
        a: ({ href, children: c }) => (
          <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            className="text-primary underline"
          >
            {c}
          </a>
        ),
        p: ({ children: c }) => <p className="mb-2 last:mb-0">{c}</p>,
        ul: ({ children: c }) => <ul className="mb-2 list-disc pl-5">{c}</ul>,
        ol: ({ children: c }) => <ol className="mb-2 list-decimal pl-5">{c}</ol>,
      }}
    >
      {children}
    </ReactMarkdown>
  )
}
