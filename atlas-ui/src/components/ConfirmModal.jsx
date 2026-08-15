import React from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, Typography, Box, Paper } from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import ErrorIcon from '@mui/icons-material/Error';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';

export const ConfirmModal = ({ open, title, message, confirmText = 'Confirm', cancelText = 'Cancel', severity = 'warning', onConfirm, onCancel }) => {
  if (!open) return null;

  const getIcon = () => {
    switch (severity) {
      case 'error':
        return <ErrorIcon sx={{ color: '#ef4444', fontSize: 28 }} />;
      case 'info':
        return <InfoOutlinedIcon sx={{ color: '#3b82f6', fontSize: 28 }} />;
      case 'warning':
      default:
        return <WarningAmberIcon sx={{ color: '#f59e0b', fontSize: 28 }} />;
    }
  };

  const getConfirmButtonColor = () => {
    switch (severity) {
      case 'error':
        return 'error';
      case 'info':
        return 'primary';
      case 'warning':
      default:
        return 'warning';
    }
  };

  return (
    <Dialog
      open={open}
      onClose={onCancel}
      maxWidth="xs"
      fullWidth
      slotProps={{
        paper: {
          sx: {
            borderRadius: 3,
            p: 1,
            bgcolor: 'background.paper',
            border: '1px solid',
            borderColor: 'divider',
            boxShadow: '0 20px 40px rgba(0,0,0,0.3)',
          }
        }
      }}
    >
      <DialogTitle sx={{ pb: 1, pt: 2, px: 2.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{
            width: 40,
            height: 40,
            borderRadius: '50%',
            bgcolor: severity === 'error' ? 'rgba(239,68,68,0.1)' : severity === 'info' ? 'rgba(59,130,246,0.1)' : 'rgba(245,158,11,0.1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}>
            {getIcon()}
          </Box>
          <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1.1rem' }}>
            {title || 'Confirmation Required'}
          </Typography>
        </Box>
      </DialogTitle>

      <DialogContent sx={{ px: 2.5, py: 1 }}>
        <Typography variant="body2" color="text.secondary" sx={{ fontSize: '14px', lineHeight: 1.5 }}>
          {message}
        </Typography>
      </DialogContent>

      <DialogActions sx={{ p: 2, gap: 1 }}>
        {cancelText && (
          <Button
            variant="outlined"
            size="small"
            onClick={onCancel}
            sx={{
              borderRadius: 2,
              px: 2.5,
              textTransform: 'none',
              fontWeight: 600,
              color: 'text.secondary',
              borderColor: 'divider'
            }}
          >
            {cancelText}
          </Button>
        )}
        <Button
          variant="contained"
          size="small"
          color={getConfirmButtonColor()}
          onClick={onConfirm}
          autoFocus
          sx={{
            borderRadius: 2,
            px: 2.5,
            textTransform: 'none',
            fontWeight: 600,
            boxShadow: 'none'
          }}
        >
          {confirmText}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
