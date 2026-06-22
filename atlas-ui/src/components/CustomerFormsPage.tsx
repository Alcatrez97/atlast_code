import React, { useEffect, useState } from 'react';
import {
  Box, Container, Paper, Typography, Button,
  Chip, IconButton, InputBase, CircularProgress, TableContainer,
  Table, TableHead, TableBody, TableRow, TableCell, Alert,
  FormControl, Select, MenuItem, Grid
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import DocumentScannerIcon from '@mui/icons-material/DocumentScanner';

interface CustomerFormDto {
  id: string;
  customerName: string;
  formStatus: string;
  updatedAt: string;
}

interface CustomerFormsPageProps {
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

export const CustomerFormsPage: React.FC<CustomerFormsPageProps> = ({ onShowNotification }) => {
  const [forms, setForms] = useState<CustomerFormDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [actioningId, setActioningId] = useState<string | null>(null);

  // Pagination & Filtering state
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [selectedStatus, setSelectedStatus] = useState('ALL');

  const fetchForms = async (
    targetPage: number = page,
    targetSize: number = size,
    statusFilter: string = selectedStatus,
    searchTerm: string = search
  ) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.append('page', String(targetPage));
      params.append('size', String(targetSize));

      if (statusFilter && statusFilter !== 'ALL') {
        params.append('status', statusFilter);
      }
      if (searchTerm && searchTerm.trim() !== '') {
        params.append('search', searchTerm.trim());
      }

      const res = await fetch(`/api/forms?${params.toString()}`);
      if (!res.ok) throw new Error('Failed to fetch simulated customer forms');
      const data = await res.json();
      setForms(data.content || []);
      setTotalElements(data.totalElements || 0);
      setTotalPages(data.totalPages || 0);
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchForms(page, size, selectedStatus, search);
  }, [page, size, selectedStatus, search]);

  const handleUpdateStatus = async (id: string, currentStatus: string, approve: boolean) => {
    // Expect status like "A2 Pending", parse out "A2"
    const parts = currentStatus.split(' ');
    if (parts.length < 2 || parts[1].toLowerCase() !== 'pending') {
      onShowNotification('Form is not in a PENDING status', 'error');
      return;
    }
    const bucketId = parts[0];
    const newStatus = approve ? `${bucketId}Accept` : `${bucketId}Reject`;

    setActioningId(id);
    try {
      const res = await fetch(`/api/forms/${id}/status`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: newStatus })
      });
      if (!res.ok) throw new Error('Failed to update form status');
      
      onShowNotification(`Form status updated to ${newStatus} successfully`, 'success');
      await fetchForms();
    } catch (err: any) {
      onShowNotification(err.message, 'error');
    } finally {
      setActioningId(null);
    }
  };

  const getStatusChip = (status: string) => {
    let icon = <HourglassEmptyIcon style={{ fontSize: 13 }} />;
    let bgColor = 'rgba(255,255,255,0.06)';
    let textColor = '#fff';

    if (status.toLowerCase().endsWith('pending')) {
      bgColor = 'rgba(245, 158, 11, 0.12)';
      textColor = '#f59e0b';
    } else if (status.toLowerCase().endsWith('accept')) {
      icon = <CheckCircleIcon style={{ fontSize: 13 }} />;
      bgColor = 'rgba(16, 185, 129, 0.12)';
      textColor = '#10b981';
    } else if (status.toLowerCase().endsWith('reject')) {
      icon = <CancelIcon style={{ fontSize: 13 }} />;
      bgColor = 'rgba(239, 68, 68, 0.12)';
      textColor = '#ef4444';
    }

    return (
      <Chip
        label={status}
        size="small"
        icon={icon}
        sx={{
          height: 24, fontSize: '11px', fontWeight: 800,
          bgcolor: bgColor, color: textColor,
          border: `1px solid ${textColor}20`,
          '& .MuiChip-icon': { color: 'inherit', mr: -0.2 }
        }}
      />
    );
  };



  const accentColor = '#ec4899'; // Premium pink/magenta for simulator page

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '92vh', py: 4, color: 'text.primary', transition: 'background-color 0.25s ease-in-out' }}>
      <Container maxWidth="lg">
        {/* Header */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4, gap: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Box sx={{
              background: 'linear-gradient(135deg, #ec4899 0%, #8b5cf6 100%)',
              borderRadius: 2.5,
              width: 44,
              height: 44,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 0 20px rgba(236, 72, 153, 0.3)'
            }}>
              <DocumentScannerIcon sx={{ color: '#fff' }} />
            </Box>
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
                Customer Forms Simulator
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Mock an external business document repository. Updating statuses here signals the workflow engine poller/listeners.
              </Typography>
            </Box>
          </Box>
          <IconButton onClick={() => fetchForms()} sx={{ color: accentColor, border: `1px solid rgba(236,72,153,0.2)` }}>
            <RefreshIcon />
          </IconButton>
        </Box>

        <Alert severity="info" sx={{
          bgcolor: 'rgba(139, 92, 246, 0.05)',
          border: '1px solid rgba(139, 92, 246, 0.15)',
          color: '#cbd5e1',
          mb: 4,
          borderRadius: 2,
          '& .MuiAlert-icon': { color: '#8b5cf6' }
        }}>
          <strong>Integration Guide:</strong> When a running workflow instance encounters a <em>BUCKET</em> task, it suspends execution and inserts a pending form record here with status <code>&lt;BucketId&gt; Pending</code>. Approving or rejecting the form updates it to <code>&lt;BucketId&gt;Accept</code> or <code>&lt;BucketId&gt;Reject</code> which immediately triggers resumption.
        </Alert>

        {/* Filter Panel */}
        <Paper sx={{
          p: 3, mb: 4,
          bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 3
        }}>
          <Grid container spacing={2} sx={{ alignItems: 'center' }}>
            {/* Search Input */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: '0.5px', display: 'block', mb: 1 }}>
                SEARCH CUSTOMER DOCUMENT
              </Typography>
              <Box sx={{
                p: '4px 12px', display: 'flex', alignItems: 'center',
                bgcolor: 'background.default', border: '1px solid', borderColor: 'divider', borderRadius: 2
              }}>
                <SearchIcon sx={{ color: 'text.secondary', mr: 1, fontSize: 20 }} />
                <InputBase
                  placeholder="Search by ID, Customer Name, or Status..."
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value);
                    setPage(0);
                  }}
                  sx={{ ml: 1, flex: 1, color: 'text.primary', fontSize: '14px' }}
                />
              </Box>
            </Grid>

            {/* Status Filter Dropdown */}
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: '0.5px', display: 'block', mb: 1 }}>
                FILTER BY STATUS
              </Typography>
              <FormControl fullWidth size="small">
                <Select
                  value={selectedStatus}
                  onChange={(e) => {
                    setSelectedStatus(e.target.value as string);
                    setPage(0);
                  }}
                  sx={{
                    bgcolor: 'background.default',
                    borderRadius: 2,
                    fontSize: '14px',
                    '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' }
                  }}
                >
                  <MenuItem value="ALL" sx={{ fontSize: '14px' }}>All Statuses</MenuItem>
                  <MenuItem value="PENDING" sx={{ fontSize: '14px' }}>PENDING</MenuItem>
                  <MenuItem value="ACCEPT" sx={{ fontSize: '14px' }}>APPROVED / ACCEPT</MenuItem>
                  <MenuItem value="REJECT" sx={{ fontSize: '14px' }}>REJECTED / REJECT</MenuItem>
                </Select>
              </FormControl>
            </Grid>

            {/* Actions (Reset) */}
            <Grid size={{ xs: 12, sm: 6, md: 2 }} sx={{ display: 'flex', gap: 1, alignSelf: 'flex-end', height: 40 }}>
              <Button
                variant="outlined"
                fullWidth
                onClick={() => {
                  setSearch('');
                  setSelectedStatus('ALL');
                  setPage(0);
                }}
                sx={{
                  borderRadius: 2,
                  fontWeight: 700,
                  fontSize: '12px',
                  borderColor: 'divider',
                  color: 'text.secondary',
                  '&:hover': {
                    borderColor: accentColor,
                    color: accentColor,
                    bgcolor: 'rgba(236,72,153,0.04)'
                  }
                }}
              >
                Clear
              </Button>
            </Grid>
          </Grid>
        </Paper>

        {/* Content Table */}
        {loading && forms.length === 0 ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
            <CircularProgress color="secondary" />
          </Box>
        ) : forms.length === 0 ? (
          <Box sx={{
            p: 8, border: '1px dashed', borderColor: 'divider', borderRadius: 2,
            textAlign: 'center', bgcolor: 'background.paper'
          }}>
            <Typography variant="body1" color="text.secondary">
              No simulated customer form records found.
            </Typography>
          </Box>
        ) : (
          <TableContainer component={Paper} sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
            <Table>
              <TableHead>
                <TableRow sx={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
                  <TableCell sx={{ color: 'text.secondary', fontWeight: 800, fontSize: '11px', border: 'none' }}>FORM ID (CONTEXT_ID)</TableCell>
                  <TableCell sx={{ color: 'text.secondary', fontWeight: 800, fontSize: '11px', border: 'none' }}>CUSTOMER NAME</TableCell>
                  <TableCell sx={{ color: 'text.secondary', fontWeight: 800, fontSize: '11px', border: 'none' }}>CURRENT STATUS</TableCell>
                  <TableCell sx={{ color: 'text.secondary', fontWeight: 800, fontSize: '11px', border: 'none' }}>LAST UPDATED</TableCell>
                  <TableCell sx={{ color: 'text.secondary', fontWeight: 800, fontSize: '11px', border: 'none', textAlign: 'right' }}>SIMULATOR ACTIONS</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {forms.map((form) => {
                  const isPending = form.formStatus.toLowerCase().endsWith('pending');
                  const isActioning = actioningId === form.id;

                  return (
                    <TableRow key={form.id} sx={{
                      borderBottom: '1px solid rgba(255,255,255,0.04)',
                      '&:hover': { bgcolor: 'rgba(255,255,255,0.01)' }
                    }}>
                      <TableCell sx={{ fontFamily: 'monospace', color: '#cbd5e1', fontSize: '13px', border: 'none' }}>
                        {form.id}
                      </TableCell>
                      <TableCell sx={{ fontWeight: 600, border: 'none' }}>
                        {form.customerName || 'N/A'}
                      </TableCell>
                      <TableCell sx={{ border: 'none' }}>
                        {getStatusChip(form.formStatus)}
                      </TableCell>
                      <TableCell sx={{ color: 'text.secondary', fontSize: '12px', border: 'none' }}>
                        {new Date(form.updatedAt).toLocaleString()}
                      </TableCell>
                      <TableCell sx={{ border: 'none', textAlign: 'right' }}>
                        {isPending ? (
                          <Box sx={{ display: 'inline-flex', gap: 1 }}>
                            <Button
                              variant="contained"
                              size="small"
                              color="success"
                              startIcon={isActioning ? <CircularProgress size={12} color="inherit" /> : <CheckCircleIcon />}
                              onClick={() => handleUpdateStatus(form.id, form.formStatus, true)}
                              disabled={isActioning}
                              sx={{ textTransform: 'none', fontWeight: 700, px: 2, borderRadius: 1.5 }}
                            >
                              Approve
                            </Button>
                            <Button
                              variant="contained"
                              size="small"
                              color="error"
                              startIcon={isActioning ? <CircularProgress size={12} color="inherit" /> : <CancelIcon />}
                              onClick={() => handleUpdateStatus(form.id, form.formStatus, false)}
                              disabled={isActioning}
                              sx={{ textTransform: 'none', fontWeight: 700, px: 2, borderRadius: 1.5 }}
                            >
                              Reject
                            </Button>
                          </Box>
                        ) : (
                          <Typography variant="caption" sx={{ color: 'text.secondary', fontStyle: 'italic' }}>
                            Resolved/Locked
                          </Typography>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        {/* Pagination Bar */}
        {totalElements > 0 && (
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 4, flexWrap: 'wrap', gap: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <Typography variant="body2" color="text.secondary">
                Rows per page:
              </Typography>
              <FormControl size="small" sx={{ minWidth: 70 }}>
                <Select
                  value={size}
                  onChange={(e) => {
                    setSize(Number(e.target.value));
                    setPage(0);
                  }}
                  sx={{
                    bgcolor: 'background.paper',
                    borderRadius: 1.5,
                    fontSize: '13px',
                    height: 32,
                    '& .MuiOutlinedInput-notchedOutline': { borderColor: 'divider' }
                  }}
                >
                  <MenuItem value={5} sx={{ fontSize: '13px' }}>5</MenuItem>
                  <MenuItem value={10} sx={{ fontSize: '13px' }}>10</MenuItem>
                  <MenuItem value={25} sx={{ fontSize: '13px' }}>25</MenuItem>
                  <MenuItem value={50} sx={{ fontSize: '13px' }}>50</MenuItem>
                </Select>
              </FormControl>
              <Typography variant="body2" color="text.secondary">
                Showing {totalElements === 0 ? 0 : page * size + 1} - {Math.min((page + 1) * size, totalElements)} of {totalElements}
              </Typography>
            </Box>

            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
              <Button
                variant="outlined"
                size="small"
                disabled={page === 0}
                onClick={() => setPage(prev => Math.max(0, prev - 1))}
                sx={{ borderRadius: 1.5, borderColor: 'divider', fontWeight: 700 }}
              >
                Previous
              </Button>
              {Array.from({ length: totalPages }).map((_, idx) => {
                const isSelected = page === idx;
                if (totalPages > 6 && idx !== 0 && idx !== totalPages - 1 && Math.abs(page - idx) > 1) {
                  if (idx === 1 && page > 2) return <span key={idx} style={{ color: '#94a3b8' }}>...</span>;
                  if (idx === totalPages - 2 && page < totalPages - 3) return <span key={idx} style={{ color: '#94a3b8' }}>...</span>;
                  return null;
                }
                return (
                  <Button
                    key={idx}
                    variant={isSelected ? 'contained' : 'outlined'}
                    size="small"
                    onClick={() => setPage(idx)}
                    sx={{
                      minWidth: 32,
                      width: 32,
                      height: 32,
                      p: 0,
                      borderRadius: 1.5,
                      fontWeight: 700,
                      borderColor: 'divider',
                      bgcolor: isSelected ? accentColor : 'transparent',
                      color: isSelected ? '#fff' : 'text.primary',
                      '&:hover': {
                        bgcolor: isSelected ? accentColor : 'rgba(255,255,255,0.04)'
                      }
                    }}
                  >
                    {idx + 1}
                  </Button>
                );
              })}
              <Button
                variant="outlined"
                size="small"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(prev => Math.min(totalPages - 1, prev + 1))}
                sx={{ borderRadius: 1.5, borderColor: 'divider', fontWeight: 700 }}
              >
                Next
              </Button>
            </Box>
          </Box>
        )}
      </Container>
    </Box>
  );
};
