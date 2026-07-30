import { create } from 'zustand';
export const useWorkflowStore = create((set) => ({
    workflows: [],
    selectedWorkflow: null,
    selectedVersion: null,
    sidebarOpen: true,
    activeRole: 'Author',
    currentView: 'dashboard',
    viewHistory: [],
    designerNodes: [],
    designerEdges: [],
    themeMode: 'brand-light',
    executions: [],
    currentExecution: null,
    replayVersion: null,
    contextSchemas: [],
    buckets: [],
    rules: [],
    integrations: [],
    instances: [],
    events: [],
    setWorkflows: (workflows) => set({ workflows }),
    setSelectedWorkflow: (selectedWorkflow) => set((state) => {
        let selectedVersion = null;
        if (selectedWorkflow?.versions?.length) {
            if (state.selectedVersion && state.selectedWorkflow?.id === selectedWorkflow.id) {
                selectedVersion = selectedWorkflow.versions.find(v => v.id === state.selectedVersion?.id) || null;
            }
            if (!selectedVersion) {
                if (selectedWorkflow.activeVersion) {
                    selectedVersion = selectedWorkflow.versions.find(v => v.version === selectedWorkflow.activeVersion) || null;
                }
                if (!selectedVersion) {
                    selectedVersion = selectedWorkflow.versions[selectedWorkflow.versions.length - 1];
                }
            }
        }
        return { selectedWorkflow, selectedVersion };
    }),
    setSelectedVersion: (selectedVersion) => set({ selectedVersion }),
    toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
    setActiveRole: (activeRole) => set({ activeRole }),
    setView: (currentView) => set((state) => {
        if (state.currentView === currentView) {
            return {};
        }
        return {
            currentView,
            viewHistory: [...state.viewHistory, state.currentView]
        };
    }),
    goBack: () => set((state) => {
        const history = [...state.viewHistory];
        const prevView = history.pop();
        if (prevView) {
            return { currentView: prevView, viewHistory: history };
        }
        return { currentView: 'dashboard', viewHistory: [] };
    }),
    setThemeMode: (themeMode) => set({ themeMode }),
    setDesignerNodes: (designerNodes) => set({ designerNodes }),
    setDesignerEdges: (designerEdges) => set({ designerEdges }),
    setExecutions: (executions) => set({ executions }),
    setCurrentExecution: (currentExecution) => set({ currentExecution }),
    setReplayVersion: (replayVersion) => set({ replayVersion }),
    setContextSchemas: (contextSchemas) => set({ contextSchemas }),
    setBuckets: (buckets) => set({ buckets }),
    setRules: (rules) => set({ rules }),
    setIntegrations: (integrations) => set({ integrations }),
    setInstances: (instances) => set({ instances }),
    setEvents: (events) => set({ events }),
}));
