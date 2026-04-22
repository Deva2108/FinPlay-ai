import GlassCard from './GlassCard';

export default function InsightCard({ icon, title, description, colorClass, delay }) {
  return (
    <GlassCard hover={true} delay={delay} className="flex gap-4 items-start relative overflow-hidden group">
      <div className={`absolute top-0 left-0 w-1 h-full ${colorClass} opacity-50 group-hover:opacity-100 transition-opacity`} />
      <div className="text-2xl">{icon}</div>
      <div>
        <h4 className="text-white font-bold text-lg mb-1">{title}</h4>
        <p className="text-gray-400 text-sm">{description}</p>
      </div>
    </GlassCard>
  );
}
