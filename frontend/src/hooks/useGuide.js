import { useState, useCallback } from 'react';

export function useGuide(initialStep = 0) {
  const [currentStep, setCurrentStep] = useState(initialStep);

  const nextStep = useCallback(() => setCurrentStep(prev => prev + 1), []);
  const resetGuide = useCallback(() => setCurrentStep(0), []);
  const setStep = useCallback((step) => setCurrentStep(step), []);

  return { currentStep, nextStep, resetGuide, setStep };
}
