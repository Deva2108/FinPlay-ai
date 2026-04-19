export default function GlassCard({ children, className = '', hover = false, delay = 0, onClick }) {
  return (
    <div
      onClick={onClick}
      className={`glass-card p-6 ${hover ? 'card-hover cursor-pointer' : ''} ${className}`}
    >
      {children}
    </div>
  );
}
