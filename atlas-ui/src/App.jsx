import React, { useEffect, useState } from 'react';
import { ThemeProvider, CssBaseline, Box, Alert, Snackbar, Typography, Button } from '@mui/material';
import { getTheme } from './theme.js';
import { Navbar } from './components/Navbar';
import { Dashboard } from './components/Dashboard';
import { ManageWorkflowsPage } from './components/ManageWorkflowsPage';
import { VersionDrawer } from './components/VersionDrawer';
import { CreateWorkflowDialog } from './components/CreateWorkflowDialog';
import { DesignerCanvas } from './components/designer/DesignerCanvas';
import { ExecutionPanel } from './components/ExecutionPanel';
import { ExecutionReplayPage } from './components/designer/ExecutionReplayPage';
import { useWorkflowStore } from './store/workflowStore.js';
import { ContextSchemaPage } from './components/ContextSchemaPage';
import { BucketRegistryPage } from './components/BucketRegistryPage';
import { RuleRegistryPage } from './components/RuleRegistryPage';
import { RuleHelpPage } from './components/RuleHelpPage';
import { BucketHelpPage } from './components/BucketHelpPage';
import { BucketWorkloadPage } from './components/BucketWorkloadPage';
import { IntegrationRegistryPage } from './components/IntegrationRegistryPage';
import { WorkflowInstancesPage } from './components/WorkflowInstancesPage';
import { CustomerFormsPage } from './components/CustomerFormsPage';
import { ExecutionHistory } from './components/ExecutionHistory';
import { EventRegistryPage } from './components/EventRegistryPage';
// Icon imports for left collapsible navigation drawer
import { FileText, Inbox, Sliders, Plug, Activity, FileSpreadsheet, LayoutDashboard, History, Bell } from 'lucide-react';
const App = () => {
    const { setWorkflows, selectedWorkflow, setSelectedWorkflow, currentView, sidebarOpen, themeMode, setView } = useWorkflowStore();
    const [createDialogOpen, setCreateDialogOpen] = useState(false);
    const [copyDialogOpen, setCopyDialogOpen] = useState(false);
    const [copyWorkflowSource, setCopyWorkflowSource] = useState(null);
    const [versionDrawerOpen, setVersionDrawerOpen] = useState(false);
    const [snackbar, setSnackbar] = useState({
        open: false,
        message: '',
        severity: 'success'
    });
    const showSnackbar = (message, severity = 'success') => {
        setSnackbar({ open: true, message, severity });
    };
    const fetchWorkflows = async () => {
        try {
            const response = await fetch('/api/workflows');
            if (!response.ok) {
                throw new Error('Failed to fetch workflows from server');
            }
            const data = await response.json();
            setWorkflows(data);
            if (selectedWorkflow) {
                const updated = data.find(w => w.id === selectedWorkflow.id);
                if (updated) {
                    setSelectedWorkflow(updated);
                }
                else {
                    setSelectedWorkflow(null);
                    setVersionDrawerOpen(false);
                }
            }
        }
        catch (error) {
            console.error(error);
            showSnackbar(error.message || 'Error connecting to backend API', 'error');
        }
    };
    useEffect(() => {
        fetchWorkflows();
    }, []);
    const handleCreateWorkflow = async (data) => {
        try {
            const response = await fetch('/api/workflows', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data),
            });
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'Failed to create workflow');
            }
            showSnackbar('Workflow created successfully with initial draft (v1)!');
            setCreateDialogOpen(false);
            fetchWorkflows();
        }
        catch (error) {
            showSnackbar(error.message, 'error');
        }
    };
    const handleCopyWorkflow = async (data) => {
        if (!copyWorkflowSource)
            return;
        try {
            const response = await fetch('/api/workflows', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data),
            });
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'Failed to create copy workflow');
            }
            let newWf = await response.json();
            if (!newWf.versions || newWf.versions.length === 0) {
                const fetchRes = await fetch(`/api/workflows/${newWf.id}`);
                if (fetchRes.ok) {
                    newWf = await fetchRes.json();
                }
            }
            let sourceVer = null;
            if (copyWorkflowSource.versions && copyWorkflowSource.versions.length > 0) {
                if (copyWorkflowSource.activeVersion) {
                    sourceVer = copyWorkflowSource.versions.find(v => v.version === copyWorkflowSource.activeVersion);
                }
                if (!sourceVer) {
                    sourceVer = [...copyWorkflowSource.versions].sort((a, b) => b.version - a.version)[0];
                }
            }
            if (sourceVer && sourceVer.definition && sourceVer.definition.nodes && sourceVer.definition.nodes.length > 0) {
                const newDraftVersion = newWf.versions?.find(v => v.version === 1);
                if (newDraftVersion) {
                    const updateRes = await fetch(`/api/workflows/versions/${newDraftVersion.id}`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(sourceVer.definition),
                    });
                    if (!updateRes.ok) {
                        const errData = await updateRes.json();
                        throw new Error(errData.error || 'Failed to update copy workflow version draft');
                    }
                }
            }
            showSnackbar('Workflow copied successfully!');
            setCopyDialogOpen(false);
            setCopyWorkflowSource(null);
            fetchWorkflows();
        }
        catch (error) {
            showSnackbar(error.message, 'error');
        }
    };
    const handleDeleteWorkflow = async (id) => {
        if (!window.confirm('Are you sure you want to delete this workflow and all its versions?')) {
            return;
        }
        try {
            const response = await fetch(`/api/workflows/${id}`, {
                method: 'DELETE',
            });
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'Failed to delete workflow');
            }
            showSnackbar('Workflow definition deleted.');
            fetchWorkflows();
        }
        catch (error) {
            showSnackbar(error.message, 'error');
        }
    };
    const handleCreateDraftVersion = async () => {
        if (!selectedWorkflow)
            return;
        try {
            let initialGraph = null;
            if (selectedWorkflow.versions && selectedWorkflow.versions.length > 0) {
                const sorted = [...selectedWorkflow.versions].sort((a, b) => b.version - a.version);
                const latestVersion = sorted[0];
                if (latestVersion && latestVersion.definition) {
                    initialGraph = JSON.parse(JSON.stringify(latestVersion.definition));
                }
            }
            const targetGraph = initialGraph || {
                nodes: [
                    { id: 'start-1', type: 'START', label: 'Start', position: { x: 250, y: 50 }, data: {} },
                    { id: 'end-1', type: 'END', label: 'End', position: { x: 250, y: 400 }, data: {} }
                ],
                edges: [
                    { id: 'e-1', source: 'start-1', target: 'end-1' }
                ],
                metadata: {}
            };
            const response = await fetch(`/api/workflows/${selectedWorkflow.id}/versions`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(targetGraph),
            });
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'Failed to create version draft');
            }
            showSnackbar('New version draft created.');
            fetchWorkflows();
        }
        catch (error) {
            showSnackbar(error.message, 'error');
        }
    };
    const handleTransitionStatus = async (versionId, status) => {
        try {
            const response = await fetch(`/api/workflows/versions/${versionId}/status`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ status }),
            });
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'Failed to transition version status');
            }
            showSnackbar(`Version status transitioned to ${status}.`);
            fetchWorkflows();
        }
        catch (error) {
            showSnackbar(error.message, 'error');
        }
    };
    const handleDeleteVersion = async (versionId) => {
        if (!window.confirm('Are you sure you want to delete this version?')) {
            return;
        }
        try {
            const response = await fetch(`/api/workflows/versions/${versionId}`, {
                method: 'DELETE',
            });
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'Failed to delete version');
            }
            showSnackbar('Workflow version deleted.');
            fetchWorkflows();
        }
        catch (error) {
            showSnackbar(error.message, 'error');
        }
    };
    const handleOpenVersions = (workflow) => {
        setSelectedWorkflow(workflow);
        setVersionDrawerOpen(true);
    };
    const sidebarNavItems = [
        { label: 'Context Schemas', view: 'contextSchema', icon: FileText },
        { label: 'Bucket Registry', view: 'buckets', icon: Inbox },
        { label: 'Rule Registry', view: 'rules', icon: Sliders },
        { label: 'Event Registry', view: 'events', icon: Bell },
        { label: 'Integrations', view: 'integrations', icon: Plug },
        { label: 'Instances', view: 'instances', icon: Activity },
        { label: 'Bucket Workload', view: 'bucketWorkload', icon: LayoutDashboard },
        { label: 'Customer Forms', view: 'customerForms', icon: FileSpreadsheet },
        { label: 'Execution History', view: 'executions', icon: History },
    ];
    return (<ThemeProvider theme={getTheme(themeMode)}>
      <CssBaseline />
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', bgcolor: 'background.default', transition: 'background-color 0.25s ease-in-out' }}>
        {currentView === 'designer' ? (<DesignerCanvas onRefreshWorkflows={fetchWorkflows} onShowNotification={showSnackbar}/>) : currentView === 'executor' ? (<ExecutionPanel onShowNotification={showSnackbar}/>) : currentView === 'replay' ? (<ExecutionReplayPage onShowNotification={showSnackbar}/>) : (<>
            <Navbar />
            <Box sx={{ display: 'flex', flexGrow: 1, overflow: 'hidden', height: 'calc(100vh - 64px)' }}>
              <Box sx={{
                width: sidebarOpen ? 260 : 64,
                flexShrink: 0,
                bgcolor: '#2F3043',
                borderRight: 'none',
                transition: 'width 0.15s ease-in-out',
                display: 'flex',
                flexDirection: 'column',
                gap: 1,
                py: 2,
                px: sidebarOpen ? 2 : 1,
                overflowX: 'hidden',
                overflowY: 'auto',
                whiteSpace: 'nowrap',
                height: '100%'
            }}>
                {sidebarNavItems.map((item) => {
                const isActive = currentView === item.view;
                const IconComponent = item.icon;
                return (<Button key={item.view} onClick={() => setView(item.view)} sx={{
                        justifyContent: sidebarOpen ? 'flex-start' : 'center',
                        px: sidebarOpen ? 2 : 0,
                        py: 1,
                        minWidth: 0,
                        minHeight: 40,
                        borderRadius: '6px',
                        borderLeft: isActive ? '3px solid #EE2737' : '3px solid transparent',
                        bgcolor: isActive ? 'rgba(238, 39, 55, 0.1)' : 'transparent',
                        color: isActive ? '#FFFFFF' : '#ACACB4',
                        '&:hover': {
                            bgcolor: isActive ? 'rgba(238, 39, 55, 0.15)' : 'rgba(255, 255, 255, 0.05)',
                        }
                    }} title={!sidebarOpen ? item.label : undefined}>
                      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', color: isActive ? '#EE2737' : '#ACACB4' }}>
                        <IconComponent size={18}/>
                      </Box>
                      {sidebarOpen && (<Typography sx={{ ml: 2, fontWeight: 500, fontSize: '14px', textTransform: 'none', color: isActive ? '#EE2737' : '#ACACB4' }}>
                          {item.label}
                        </Typography>)}
                    </Button>);
            })}
              </Box>

              <Box sx={{ flexGrow: 1, overflowY: 'auto', height: '100%' }}>
                {currentView === 'manageWorkflows' ? (<ManageWorkflowsPage onOpenCreate={() => setCreateDialogOpen(true)} onOpenVersions={handleOpenVersions} onDeleteWorkflow={handleDeleteWorkflow} onShowNotification={showSnackbar} onOpenCopy={(wf) => {
                    setCopyWorkflowSource(wf);
                    setCopyDialogOpen(true);
                }}/>) : currentView === 'contextSchema' ? (<ContextSchemaPage onShowNotification={showSnackbar}/>) : currentView === 'buckets' ? (<BucketRegistryPage onShowNotification={showSnackbar}/>) : currentView === 'rules' ? (<RuleRegistryPage onShowNotification={showSnackbar}/>) : currentView === 'ruleHelp' ? (<RuleHelpPage onShowNotification={showSnackbar}/>) : currentView === 'bucketHelp' ? (<BucketHelpPage onShowNotification={showSnackbar}/>) : currentView === 'events' ? (<EventRegistryPage onShowNotification={showSnackbar}/>) : currentView === 'integrations' ? (<IntegrationRegistryPage onShowNotification={showSnackbar}/>) : currentView === 'bucketWorkload' ? (<BucketWorkloadPage onShowNotification={showSnackbar}/>) : currentView === 'instances' ? (<WorkflowInstancesPage onShowNotification={showSnackbar}/>) : currentView === 'customerForms' ? (<CustomerFormsPage onShowNotification={showSnackbar}/>) : currentView === 'executions' ? (<ExecutionHistory onShowNotification={showSnackbar}/>) : (<Dashboard />)}
              </Box>
            </Box>
          </>)}

        <VersionDrawer open={versionDrawerOpen} onClose={() => setVersionDrawerOpen(false)} onCreateDraft={handleCreateDraftVersion} onTransitionStatus={handleTransitionStatus} onDeleteVersion={handleDeleteVersion}/>

        <CreateWorkflowDialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} onSubmit={handleCreateWorkflow}/>

        <CreateWorkflowDialog open={copyDialogOpen} onClose={() => {
            setCopyDialogOpen(false);
            setCopyWorkflowSource(null);
        }} onSubmit={handleCopyWorkflow} initialData={copyWorkflowSource ? {
            name: `${copyWorkflowSource.name} Copy`,
            key: `${copyWorkflowSource.key}_COPY`,
            description: copyWorkflowSource.description || ''
        } : null} mode="copy"/>

        <Snackbar open={snackbar.open} autoHideDuration={6000} onClose={() => setSnackbar({ ...snackbar, open: false })} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}>
          <Alert onClose={() => setSnackbar({ ...snackbar, open: false })} severity={snackbar.severity} sx={{ width: '100%', borderRadius: 2 }}>
            {snackbar.message}
          </Alert>
        </Snackbar>
      </Box>
    </ThemeProvider>);
};
export default App;
