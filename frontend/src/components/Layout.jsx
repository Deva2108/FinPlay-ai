import Topbar from './Topbar';
import StockDetailPanel from './StockDetailPanel';
import { useStockPanel } from '../context/StockPanelContext';

export default function Layout({ children }) {
  const { selectedStock, isOpen, closeStockPanel } = useStockPanel();

  return (
    <div className="flex flex-col bg-[#020617] text-slate-200 min-h-screen overflow-x-hidden">
      <Topbar />
      <div className="flex-1 w-full">
        {children}
      </div>
      <StockDetailPanel 
        stock={selectedStock} 
        isOpen={isOpen} 
        onClose={closeStockPanel} 
      />
    </div>
  );
}
