import React, { useState } from 'react';
import { Drawer, Box, Typography, Button, IconButton, Stepper, Step, StepLabel, StepContent, Chip, Alert, Card, CardContent } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import AddIcon from '@mui/icons-material/Add';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import RateReviewIcon from '@mui/icons-material/RateReview';
import LockIcon from '@mui/icons-material/Lock';
import HistoryIcon from '@mui/icons-material/History';
import EditIcon from '@mui/icons-material/Edit';
import VisibilityIcon from '@mui/icons-material/Visibility';
import { useWorkflowStore } from '../store/workflowStore';
import type { WorkflowVersion } from '../store/workflowStore';

interface VersionDrawerProps {
  open: boolean;
  onClose: () => void;
  onCreateDraft: () => void;
  onTransitionStatus: (versionId: string, status: string) => void;
  onDeleteVersion: (versionId: string) => void;
}

export const VersionDrawer: React.FC<VersionDrawerProps> = ({
  open,
  onClose,
  onCreateDraft,
  onTransitionStatus,
  onDeleteVersion
}) => {
  const { selectedWorkflow, activeRole, setSelectedVersion, setView } = useWorkflowStore();
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  if (!selectedWorkflow) return null;

  const handleOpenDesigner = (version: WorkflowVersion) => {
    setSelectedVersion(version);
    setView('designer');
    onClose();
  };

  const sortedVersions = [...(selectedWorkflow.versions || [])].sort((a, b) => b.version - a.version);

  const getStatusColor = (status: string) => {
    switch (status.toUpperCase()) {
      case 'DRAFT': return 'default';
      case 'REVIEW': return 'warning';
      case 'APPROVED': return 'info';
      case 'PUBLISHED': return 'success';
      default: return 'default';
    }
  };

  const handleTransition = (version: WorkflowVersion, targetStatus: string) => {
    setErrorMsg(null);
    const currentStatus = version.status.toUpperCase();

    // Check role authorization
    if (targetStatus === 'REVIEW' && activeRole !== 'Author' && activeRole !== 'Publisher' && activeRole !== 'Reviewer') {
      setErrorMsg('Only an Author can submit for review.');
      return;
    }
    if ((targetStatus === 'APPROVED' || (targetStatus === 'DRAFT' && currentStatus === 'REVIEW')) && activeRole !== 'Reviewer') {
      setErrorMsg('Only a Reviewer can Approve or Reject versions.');
      return;
    }
    if (targetStatus === 'PUBLISHED' && activeRole !== 'Publisher') {
      setErrorMsg('Only a Publisher can publish approved versions.');
      return;
    }

    onTransitionStatus(version.id, targetStatus);
  };

  return (
    <Drawer anchor="right" open={open} onClose={onClose} slotProps={{ paper: { sx: { width: 450, bgcolor: 'background.paper', borderLeft: '1px solid', borderColor: 'divider' } } }}>
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', p: 3, borderBottom: '1px solid rgba(36, 36, 36, 0.34)' }}>
          <HistoryIcon sx={{ mr: 1, color: 'info.main' }} />
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>Version Management</Typography>
            <Typography variant="caption" color="text.secondary">{selectedWorkflow.name} ({selectedWorkflow.key})</Typography>
          </Box>
          <IconButton onClick={onClose} size="small">
            <CloseIcon />
          </IconButton>
        </Box>

        {/* Content */}
        <Box sx={{ flexGrow: 1, overflow: 'auto', p: 3, display: 'flex', flexDirection: 'column', gap: 3 }}>
          {errorMsg && (
            <Alert severity="error" onClose={() => setErrorMsg(null)} sx={{ borderRadius: 2 }}>
              {errorMsg}
            </Alert>
          )}

          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, color: 'text.primary' }}>Version List</Typography>
            <Button
              variant="outlined"
              size="small"
              startIcon={<AddIcon />}
              onClick={onCreateDraft}
              sx={{ borderColor: 'rgba(44, 42, 42, 1)', color: 'text.primary' }}
            >
              New Draft
            </Button>
          </Box>

          {sortedVersions.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 6, border: '1px dashed rgba(7, 7, 7, 0.43)', borderRadius: 4 }}>
              <Typography color="text.secondary">No versions available. Create a draft version to start.</Typography>
            </Box>
          ) : (
            <Stepper orientation="vertical" connector={null} sx={{ p: 0 }}>
              {sortedVersions.map((ver) => {
                const isActive = selectedWorkflow.activeVersion === ver.version;
                const isPublished = ver.status.toUpperCase() === 'PUBLISHED';

                return (
                  <Step key={ver.id} active expanded>
                    <StepLabel icon={
                      <Box sx={{
                        width: 12,
                        height: 12,
                        borderRadius: '50%',
                        bgcolor: isActive ? 'success.main' : isPublished ? 'info.main' : 'primary.main',
                        boxShadow: isActive ? '0 0 10px rgba(46, 125, 50, 0.8)' : 'none',
                        mr: 1
                      }} />
                    }>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                          Version {ver.version}
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                          {isActive && <Chip size="small" label="ACTIVE" color="success" variant="filled" sx={{ height: 20, fontSize: '9px', fontWeight: 800 }} />}
                          <Chip size="small" label={ver.status} color={getStatusColor(ver.status)} variant="outlined" sx={{ height: 20, fontSize: '9px', fontWeight: 700 }} />
                        </Box>
                      </Box>
                    </StepLabel>
                    <StepContent sx={{ borderLeft: '1px solid rgba(255, 255, 255, 0.08)', ml: 0.7, pl: 2, pb: 2 }}>
                      <Card sx={{ mt: 1, bgcolor: 'rgba(255, 255, 255, 0.02)', boxShadow: '0 0 1px 1px text.primary', }}>
                        <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
                          <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>
                            Created: {new Date(ver.createdAt).toLocaleString()} by {ver.createdBy}
                          </Typography>

                          {/* Graph Summary */}
                          <Box sx={{ display: 'flex', gap: 2, mt: 1, mb: 2 }}>
                            <Typography variant="caption" color="text.secondary">
                              <strong>Nodes:</strong> {ver.definition?.nodes?.length || 0}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              <strong>Edges:</strong> {ver.definition?.edges?.length || 0}
                            </Typography>
                          </Box>

                          {/* Governance Action Buttons */}
                          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                            {ver.status.toUpperCase() === 'DRAFT' && (
                              <>
                                <Button
                                  size="small"
                                  variant="contained"
                                  color="warning"
                                  onClick={() => handleTransition(ver, 'REVIEW')}
                                  startIcon={<RateReviewIcon sx={{ fontSize: 12 }} />}
                                >
                                  Submit Review
                                </Button>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  sx={{ color: 'text.primary' }}
                                  onClick={() => handleOpenDesigner(ver)}
                                  startIcon={<EditIcon sx={{ fontSize: 12 }} />}
                                >
                                  Edit Design
                                </Button>
                              </>
                            )}

                            {ver.status.toUpperCase() === 'REVIEW' && (
                              <>
                                <Button
                                  size="small"
                                  variant="contained"
                                  color="success"
                                  onClick={() => handleTransition(ver, 'APPROVED')}
                                  startIcon={<CheckCircleOutlinedIcon />}
                                >
                                  Approve
                                </Button>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  color="error"
                                  onClick={() => handleTransition(ver, 'DRAFT')}
                                >
                                  Reject
                                </Button>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  color="info"
                                  onClick={() => handleOpenDesigner(ver)}
                                  startIcon={<VisibilityIcon sx={{ fontSize: 12 }} />}
                                >
                                  View Design
                                </Button>
                              </>
                            )}

                            {ver.status.toUpperCase() === 'APPROVED' && (
                              <>
                                <Button
                                  size="small"
                                  variant="contained"
                                  color="secondary"
                                  onClick={() => handleTransition(ver, 'PUBLISHED')}
                                  startIcon={<LockIcon />}
                                >
                                  Publish Active
                                </Button>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  color="warning"
                                  onClick={() => handleTransition(ver, 'DRAFT')}
                                >
                                  Revert Draft
                                </Button>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  color="info"
                                  onClick={() => handleOpenDesigner(ver)}
                                  startIcon={<VisibilityIcon sx={{ fontSize: 12 }} />}
                                >
                                  View Design
                                </Button>
                              </>
                            )}

                            {ver.status.toUpperCase() === 'PUBLISHED' && (
                              <>
                                <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic', display: 'flex', alignItems: 'center', gap: 0.5 }}>
                                  <LockIcon sx={{ fontSize: 12 }} /> Immutable published snapshot
                                </Typography>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  color="info"
                                  onClick={() => handleOpenDesigner(ver)}
                                  startIcon={<VisibilityIcon sx={{ fontSize: 12 }} />}
                                >
                                  View Design
                                </Button>
                              </>
                            )}

                            {!isPublished && (
                              <Button
                                size="small"
                                color="error"
                                onClick={() => onDeleteVersion(ver.id)}
                                sx={{ ml: 'auto' }}
                              >
                                Delete
                              </Button>
                            )}
                          </Box>
                        </CardContent>
                      </Card>
                    </StepContent>
                  </Step>
                );
              })}
            </Stepper>
          )}
        </Box>
      </Box>
    </Drawer>
  );
};
