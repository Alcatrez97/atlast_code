import React, { useEffect, useState } from 'react';
import { ThemeProvider, CssBaseline, Box, Alert, Snackbar, Typography, Button } from '@mui/material';
import { getTheme } from './theme';
import { Navbar } from './components/Navbar';
import { Dashboard } from './components/Dashboard';
import { ManageWorkflowsPage } from './components/ManageWorkflowsPage';
import { VersionDrawer } from './components/VersionDrawer';
import { CreateWorkflowDialog } from './components/CreateWorkflowDialog';
import { DesignerCanvas } from './components/designer/DesignerCanvas';
import { ExecutionPanel } from './components/ExecutionPanel';
import { ExecutionReplayPage } from './components/designer/ExecutionReplayPage';
import { useWorkflowStore } from './store/workflowStore';
import type { WorkflowDefinition } from './store/workflowStore';
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
import AssignmentIcon from '@mui/icons-material/Assignment';
import InboxIcon from '@mui/icons-material/Inbox';
import RuleIcon from '@mui/icons-material/Rule';
import SettingsInputComponentIcon from '@mui/icons-material/SettingsInputComponent';
import AutoModeIcon from '@mui/icons-material/AutoMode';
import ListAltIcon from '@mui/icons-material/ListAlt';
import AssessmentIcon from '@mui/icons-material/Assessment';
import HistoryIcon from '@mui/icons-material/History';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';

const App: React.FC = () => {
  const { setWorkflows, selectedWorkflow, setSelectedWorkflow, currentView, sidebarOpen, themeMode, setView } = useWorkflowStore();

  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [copyDialogOpen, setCopyDialogOpen] = useState(false);
  const [copyWorkflowSource, setCopyWorkflowSource] = useState<WorkflowDefinition | null>(null);
  const [versionDrawerOpen, setVersionDrawerOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({
    open: false,
    message: '',
    severity: 'success'
  });

  const showSnackbar = (message: string, severity: 'success' | 'error' = 'success') => {
    setSnackbar({ open: true, message, severity });
  };

  const fetchWorkflows = async () => {
    try {
      const response = await fetch('/api/workflows');
      if (!response.ok) {
        throw new Error('Failed to fetch workflows from server');
      }
      const data: WorkflowDefinition[] = await response.json();
      setWorkflows(data);

      // If a workflow is selected, update it in state to keep details fresh
      if (selectedWorkflow) {
        const updated = data.find(w => w.id === selectedWorkflow.id);
        if (updated) {
          setSelectedWorkflow(updated);
        } else {
          setSelectedWorkflow(null);
          setVersionDrawerOpen(false);
        }
      }
    } catch (error: any) {
      console.error(error);
      showSnackbar(error.message || 'Error connecting to backend API', 'error');
    }
  };

  useEffect(() => {
    fetchWorkflows();
  }, []);

  const handleCreateWorkflow = async (data: { name: string; key: string; description: string }) => {
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
    } catch (error: any) {
      showSnackbar(error.message, 'error');
    }
  };

  const handleCopyWorkflow = async (data: { name: string; key: string; description: string }) => {
    if (!copyWorkflowSource) return;
    try {
      // Step 1: Create the new workflow definition
      const response = await fetch('/api/workflows', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || 'Failed to create copy workflow');
      }

      let newWf: WorkflowDefinition = await response.json();

      // Fallback: if versions is empty/missing, retrieve it explicitly
      if (!newWf.versions || newWf.versions.length === 0) {
        const fetchRes = await fetch(`/api/workflows/${newWf.id}`);
        if (fetchRes.ok) {
          newWf = await fetchRes.json();
        }
      }

      // Step 2: Identify the source version graph to copy from
      let sourceVer = null;
      if (copyWorkflowSource.versions && copyWorkflowSource.versions.length > 0) {
        // Prioritize active published version if it exists
        if (copyWorkflowSource.activeVersion) {
          sourceVer = copyWorkflowSource.versions.find(v => v.version === copyWorkflowSource.activeVersion);
        }
        // Fallback to latest version
        if (!sourceVer) {
          sourceVer = [...copyWorkflowSource.versions].sort((a, b) => b.version - a.version)[0];
        }
      }

      // Step 3: Copy the graph to version 1 of the new workflow
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
    } catch (error: any) {
      showSnackbar(error.message, 'error');
    }
  };

  const handleDeleteWorkflow = async (id: string) => {
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
    } catch (error: any) {
      showSnackbar(error.message, 'error');
    }
  };

  const handleCreateDraftVersion = async () => {
    if (!selectedWorkflow) return;
    try {
      // Find the latest version to clone its definition graph
      let initialGraph = null;
      if (selectedWorkflow.versions && selectedWorkflow.versions.length > 0) {
        const sorted = [...selectedWorkflow.versions].sort((a, b) => b.version - a.version);
        const latestVersion = sorted[0];
        if (latestVersion && latestVersion.definition) {
          // Deep-copy to avoid reference issues
          initialGraph = JSON.parse(JSON.stringify(latestVersion.definition));
        }
      }

      // Fallback to default graph if no previous version/definition exists
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
    } catch (error: any) {
      showSnackbar(error.message, 'error');
    }
  };

  const handleTransitionStatus = async (versionId: string, status: string) => {
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
    } catch (error: any) {
      showSnackbar(error.message, 'error');
    }
  };

  const handleDeleteVersion = async (versionId: string) => {
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
    } catch (error: any) {
      showSnackbar(error.message, 'error');
    }
  };

  const handleOpenVersions = (workflow: WorkflowDefinition) => {
    setSelectedWorkflow(workflow);
    setVersionDrawerOpen(true);
  };

  const sidebarNavItems = [
    { label: 'Context Schemas', view: 'contextSchema', icon: <AssignmentIcon /> },
    { label: 'Bucket Registry', view: 'buckets', icon: <InboxIcon /> },
    { label: 'Rule Registry', view: 'rules', icon: <RuleIcon /> },
    { label: 'Event Registry', view: 'events', icon: <NotificationsActiveIcon /> },
    { label: 'Integrations', view: 'integrations', icon: <SettingsInputComponentIcon /> },
    { label: 'Instances', view: 'instances', icon: <AutoModeIcon /> },
    { label: 'Bucket Workload', view: 'bucketWorkload', icon: <AssessmentIcon /> },
    { label: 'Customer Forms', view: 'customerForms', icon: <ListAltIcon /> },
    { label: 'Execution History', view: 'executions', icon: <HistoryIcon /> },
  ];

  return (
    <ThemeProvider theme={getTheme(themeMode)}>
      <CssBaseline />
      <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', bgcolor: 'background.default', transition: 'background-color 0.25s ease-in-out' }}>
        {currentView === 'designer' ? (
          <DesignerCanvas
            onRefreshWorkflows={fetchWorkflows}
            onShowNotification={showSnackbar}
          />
        ) : currentView === 'executor' ? (
          <ExecutionPanel onShowNotification={showSnackbar} />
        ) : currentView === 'replay' ? (
          <ExecutionReplayPage onShowNotification={showSnackbar} />
        ) : (
          <>
            <Navbar />
            <Box sx={{ display: 'flex', flexGrow: 1, position: 'relative' }}>
              {/* Left Collapsible Sidebar Drawer */}
              <Box
                sx={{
                  width: sidebarOpen ? 260 : 70,
                  flexShrink: 0,
                  bgcolor: 'background.paper',
                  borderRight: '1px solid',
                  borderColor: 'divider',
                  transition: 'width 0.2s ease-in-out, padding 0.2s ease-in-out',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 1,
                  py: 3,
                  px: sidebarOpen ? 2 : 1,
                  overflowX: 'hidden',
                  whiteSpace: 'nowrap'
                }}
              >
                {sidebarNavItems.map((item) => {
                  const isActive = currentView === item.view;
                  return (
                    <Button
                      key={item.view}
                      onClick={() => setView(item.view as any)}
                      variant={isActive ? 'contained' : 'text'}
                      color={isActive ? 'primary' : 'inherit'}
                      sx={{
                        justifyContent: sidebarOpen ? 'flex-start' : 'center',
                        px: sidebarOpen ? 2 : 0,
                        py: 1.5,
                        minWidth: 0,
                        borderRadius: 2,
                        color: isActive ? '#fff' : 'text.secondary',
                        '&:hover': {
                          bgcolor: isActive ? 'primary.main' : 'action.hover',
                        }
                      }}
                      title={!sidebarOpen ? item.label : undefined}
                    >
                      {item.icon}
                      {sidebarOpen && (
                        <Typography sx={{ ml: 1.5, fontWeight: 700, fontSize: '14px' }}>
                          {item.label}
                        </Typography>
                      )}
                    </Button>
                  );
                })}
              </Box>

              {/* Main Page Area */}
              <Box sx={{ flexGrow: 1, overflowY: 'auto' }}>
                {currentView === 'manageWorkflows' ? (
                  <ManageWorkflowsPage
                    onOpenCreate={() => setCreateDialogOpen(true)}
                    onOpenVersions={handleOpenVersions}
                    onDeleteWorkflow={handleDeleteWorkflow}
                    onShowNotification={showSnackbar}
                    onOpenCopy={(wf) => {
                      setCopyWorkflowSource(wf);
                      setCopyDialogOpen(true);
                    }}
                  />
                ) : currentView === 'contextSchema' ? (
                  <ContextSchemaPage onShowNotification={showSnackbar} />
                ) : currentView === 'buckets' ? (
                  <BucketRegistryPage onShowNotification={showSnackbar} />
                ) : currentView === 'rules' ? (
                  <RuleRegistryPage onShowNotification={showSnackbar} />
                ) : currentView === 'ruleHelp' ? (
                  <RuleHelpPage onShowNotification={showSnackbar} />
                ) : currentView === 'bucketHelp' ? (
                  <BucketHelpPage onShowNotification={showSnackbar} />
                ) : currentView === 'events' ? (
                  <EventRegistryPage onShowNotification={showSnackbar} />
                ) : currentView === 'integrations' ? (
                  <IntegrationRegistryPage onShowNotification={showSnackbar} />
                ) : currentView === 'bucketWorkload' ? (
                  <BucketWorkloadPage onShowNotification={showSnackbar} />
                ) : currentView === 'instances' ? (
                  <WorkflowInstancesPage onShowNotification={showSnackbar} />
                ) : currentView === 'customerForms' ? (
                  <CustomerFormsPage onShowNotification={showSnackbar} />
                ) : currentView === 'executions' ? (
                  <ExecutionHistory onShowNotification={showSnackbar} />
                ) : (
                  <Dashboard />
                )}
              </Box>
            </Box>
          </>
        )}

        {/* Drawer for Version Timeline */}
        <VersionDrawer
          open={versionDrawerOpen}
          onClose={() => setVersionDrawerOpen(false)}
          onCreateDraft={handleCreateDraftVersion}
          onTransitionStatus={handleTransitionStatus}
          onDeleteVersion={handleDeleteVersion}
        />

        {/* Dialog for Workflow Creation */}
        <CreateWorkflowDialog
          open={createDialogOpen}
          onClose={() => setCreateDialogOpen(false)}
          onSubmit={handleCreateWorkflow}
        />

        {/* Dialog for Workflow Copy */}
        <CreateWorkflowDialog
          open={copyDialogOpen}
          onClose={() => {
            setCopyDialogOpen(false);
            setCopyWorkflowSource(null);
          }}
          onSubmit={handleCopyWorkflow}
          initialData={copyWorkflowSource ? {
            name: `${copyWorkflowSource.name} Copy`,
            key: `${copyWorkflowSource.key}_COPY`,
            description: copyWorkflowSource.description || ''
          } : null}
          mode="copy"
        />

        {/* Snackbar Notification */}
        <Snackbar
          open={snackbar.open}
          autoHideDuration={6000}
          onClose={() => setSnackbar({ ...snackbar, open: false })}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        >
          <Alert
            onClose={() => setSnackbar({ ...snackbar, open: false })}
            severity={snackbar.severity}
            sx={{ width: '100%', borderRadius: 2 }}
          >
            {snackbar.message}
          </Alert>
        </Snackbar>
      </Box>
    </ThemeProvider>
  );
};

export default App;
