import React from 'react';
import {
  Box, Container, Typography, Button, Card, CardContent,
  IconButton, Paper, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Grid
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import HelpIcon from '@mui/icons-material/Help';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import CodeIcon from '@mui/icons-material/Code';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import DeviceHubIcon from '@mui/icons-material/DeviceHub';
import { useWorkflowStore } from '../store/workflowStore';

interface BucketHelpPageProps {
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

export const BucketHelpPage: React.FC<BucketHelpPageProps> = ({ onShowNotification }) => {
  const { goBack } = useWorkflowStore();

  const handleCopyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    onShowNotification('Copied snippet to clipboard!', 'success');
  };

  const contextFields = [
    {
      field: 'context.lastOutcome',
      type: 'String',
      description: 'The outcome/resolution value of the resolved bucket (e.g., "APPROVED", "REJECTED", "RETRY").'
    },
    {
      field: 'context.lastBucketId',
      type: 'String',
      description: 'The business key of the bucket node that was just resolved (e.g., "HIGH_RISK_REVIEWS").'
    },
    {
      field: 'context.form_status',
      type: 'String',
      description: 'Signifies the form status mapping. Always resolved to "RESOLVED" upon completion.'
    },
    {
      field: 'context.notes',
      type: 'String',
      description: 'The manual validation notes entered during resolution.'
    }
  ];

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '92vh', py: 4, color: 'text.primary' }}>
      <Container maxWidth="lg">
        {/* Title bar */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
          <IconButton onClick={() => goBack()} sx={{ color: '#a855f7', border: '1px solid rgba(168,85,247,0.2)' }}>
            <ArrowBackIcon />
          </IconButton>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <HelpIcon sx={{ color: '#a855f7', fontSize: 32 }} />
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
                Outcome Bucket Engine Guide
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Learn how outcome buckets work, what fields are injected, and how to route downstream branches.
              </Typography>
            </Box>
          </Box>
        </Box>

        <Grid container spacing={4}>
          {/* Main Info Section */}
          <Grid size={{ xs: 12, md: 8 }}>
            <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', mb: 4, borderRadius: 2 }}>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                  <DeviceHubIcon sx={{ color: '#a855f7' }} />
                  What Happens when a Bucket Node is Encountered?
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  In Project Atlas, a <b>BUCKET node</b> is a stateful wait-endpoint representing a manual review or operational task. Here is the lifecycle of how the engine runs it:
                </Typography>

                <Box sx={{ pl: 2, borderLeft: '3px solid #a855f7', my: 3 }}>
                  <Box sx={{ mb: 2.5 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'text.primary' }}>
                      1. Node Suspension & Record Creation
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      When the <code>GraphTraversalEngine</code> traverses onto a Bucket node, it halts traversal. The workflow instance status is set to <code>WAITING</code>, and a <code>BucketExecution</code> task is written in the registry database with a <code>PENDING</code> status.
                    </Typography>
                  </Box>

                  <Box sx={{ mb: 2.5 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'text.primary' }}>
                      2. Manual / External Resolution
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Operations agents or automated systems review the transaction and submit a decision (e.g. Approved, Rejected, Escalate) using the form API.
                    </Typography>
                  </Box>

                  <Box sx={{ mb: 0 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'text.primary' }}>
                      3. Context Injection & Downstream Resume
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Upon resolution, the <code>BucketResolutionService</code> injects the decision outcome, bucket metadata, and logs into the running workflow's <code>Context Map</code>, and triggers node traversal resume.
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>

            {/* Injected Fields Section */}
            <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', mb: 4, borderRadius: 2 }}>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                  <InfoOutlinedIcon sx={{ color: '#a855f7' }} />
                  Injected Context Variables
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  When a bucket task resolves, the following variables are dynamically written to the workflow instance's context map. You can use these in rule nodes or transition conditions downstream:
                </Typography>

                <TableContainer component={Paper} sx={{ bgcolor: 'background.default', border: '1px solid', borderColor: 'divider', borderRadius: 2, boxShadow: 'none' }}>
                  <Table size="small">
                    <TableHead sx={{ bgcolor: 'rgba(255, 255, 255, 0.02)' }}>
                      <TableRow>
                        <TableCell sx={{ fontWeight: 700 }}>Variable Path</TableCell>
                        <TableCell sx={{ fontWeight: 700 }}>Data Type</TableCell>
                        <TableCell sx={{ fontWeight: 700 }}>Description</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {contextFields.map((row) => (
                        <TableRow key={row.field}>
                          <TableCell sx={{ fontFamily: 'monospace', color: '#a855f7', fontWeight: 600 }}>{row.field}</TableCell>
                          <TableCell sx={{ color: 'text.primary' }}><code>{row.type}</code></TableCell>
                          <TableCell sx={{ color: 'text.secondary', fontSize: '12px' }}>{row.description}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </CardContent>
            </Card>

            {/* Downstream Routing section */}
            <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                  <AccountTreeIcon sx={{ color: '#a855f7' }} />
                  Routing Downstream branches
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Connect edges outbound from the Bucket node or Rule nodes downstream. Use Spring SpEL expressions inside transition conditions to evaluate outcomes:
                </Typography>

                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <Paper sx={{ p: 2, bgcolor: 'background.default', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, fontFamily: 'monospace', color: '#a855f7' }}>
                        context.lastOutcome == 'APPROVED'
                      </Typography>
                      <Button size="small" startIcon={<CodeIcon sx={{ fontSize: 14 }} />} onClick={() => handleCopyToClipboard("context.lastOutcome == 'APPROVED'")} sx={{ textTransform: 'none', fontSize: '11px', color: '#a855f7' }}>
                        Copy
                      </Button>
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      Routes the flow if the decision made on the manual approval page was approved.
                    </Typography>
                  </Paper>

                  <Paper sx={{ p: 2, bgcolor: 'background.default', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, fontFamily: 'monospace', color: '#a855f7' }}>
                        context.lastOutcome == 'REJECTED' && context.lastBucketId == 'RISK_VERIFICATION'
                      </Typography>
                      <Button size="small" startIcon={<CodeIcon sx={{ fontSize: 14 }} />} onClick={() => handleCopyToClipboard("context.lastOutcome == 'REJECTED' && context.lastBucketId == 'RISK_VERIFICATION'")} sx={{ textTransform: 'none', fontSize: '11px', color: '#a855f7' }}>
                        Copy
                      </Button>
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      Routes down a rejection branch specifically if the rejection happened on the RISK_VERIFICATION bucket.
                    </Typography>
                  </Paper>
                </Box>
              </CardContent>
            </Card>
          </Grid>

          {/* Context JSON Sandbox Mock */}
          <Grid size={{ xs: 12, md: 4 }}>
            <Card sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', height: '100%', borderRadius: 2 }}>
              <CardContent sx={{ p: 3, display: 'flex', flexDirection: 'column', height: '90%' }}>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                  <CodeIcon sx={{ color: '#a855f7' }} />
                  Example Context Map
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Here is how the context looks inside a running workflow instance after resolving a bucket:
                </Typography>

                <Paper sx={{
                  p: 2, flexGrow: 1, overflow: 'auto',
                  bgcolor: 'background.default', border: '1px solid', borderColor: 'divider',
                  fontFamily: 'monospace', fontSize: '11px', borderRadius: 2,
                  maxHeight: { xs: 300, md: 'none' }
                }}>
                  <pre style={{ margin: 0, color: '#e2e8f0' }}>
{JSON.stringify({
  "amount": 25000,
  "customerId": "CUST_9918",
  "customerName": "Acme Corp",
  "riskScore": 72.5,
  "lastOutcome": "APPROVED",
  "lastBucketId": "RISK_VERIFICATION",
  "form_status": "RESOLVED",
  "notes": "Reviewed financial statement logs and audited records. Valid business case."
}, null, 2)}
                  </pre>
                </Paper>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Container>
    </Box>
  );
};
