import React, { useState, useEffect } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box } from '@mui/material';

interface CreateWorkflowDialogProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: { name: string; key: string; description: string }) => void;
  initialData?: { name: string; key: string; description: string } | null;
  mode?: 'create' | 'copy';
}

export const CreateWorkflowDialog: React.FC<CreateWorkflowDialogProps> = ({ open, onClose, onSubmit, initialData = null, mode = 'create' }) => {
  const [name, setName] = useState('');
  const [key, setKey] = useState('');
  const [description, setDescription] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (open) {
      setName(initialData?.name || '');
      setKey(initialData?.key || '');
      setDescription(initialData?.description || '');
      setErrors({});
    }
  }, [open, initialData]);

  const handleFormSubmit = () => {
    const newErrors: Record<string, string> = {};
    if (!name.trim()) newErrors.name = 'Workflow name is required';
    if (!key.trim()) {
      newErrors.key = 'Workflow key is required';
    } else if (!/^[a-zA-Z0-9_-]+$/.test(key)) {
      newErrors.key = 'Key must be alphanumeric with no spaces (e.g. Order_Validation)';
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    onSubmit({ name, key, description });
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 700, borderBottom: '1px solid', borderColor: 'divider', pb: 2 }}>
        {mode === 'copy' ? 'Copy Workflow Definition' : 'Create Workflow Definition'}
      </DialogTitle>
      <DialogContent sx={{ mt: 2 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          <TextField
            autoFocus
            label="Workflow Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            error={!!errors.name}
            helperText={errors.name}
            fullWidth
            required
            variant="outlined"
            placeholder="e.g. Order Validation Pipeline"
          />
          <TextField
            label="Workflow Key"
            value={key}
            onChange={(e) => setKey(e.target.value)}
            error={!!errors.key}
            helperText={errors.key || "System-wide unique identifier (alphanumeric, no spaces)"}
            fullWidth
            required
            variant="outlined"
            placeholder="e.g. ORDER_VALIDATION_PIPELINE"
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            fullWidth
            multiline
            rows={3}
            variant="outlined"
            placeholder="Describe the objective and execution plan of this workflow..."
          />
        </Box>
      </DialogContent>
      <DialogActions sx={{ p: 2.5, borderTop: '1px solid', borderColor: 'divider' }}>
        <Button onClick={onClose} variant="text" sx={{ color: 'text.secondary' }}>
          Cancel
        </Button>
        <Button onClick={handleFormSubmit} variant="contained" color="primary">
          {mode === 'copy' ? 'Copy Workflow' : 'Create Definition'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
