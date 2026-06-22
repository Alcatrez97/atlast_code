import React from 'react';
import { Box, Alert, AlertTitle } from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import ErrorIcon from '@mui/icons-material/Error';

interface ValidationResultBannerProps {
  validation: {
    valid: boolean;
    errors: string[];
    warnings: string[];
  } | null;
}

export const ValidationResultBanner: React.FC<ValidationResultBannerProps> = ({ validation }) => {
  if (!validation) return null;

  const hasErrors = validation.errors && validation.errors.length > 0;
  const hasWarnings = validation.warnings && validation.warnings.length > 0;

  if (!hasErrors && !hasWarnings) return null;

  return (
    <Box sx={{ m: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      {hasErrors && (
        <Alert
          severity="error"
          icon={<ErrorIcon />}
          sx={{
            borderRadius: 2,
            bgcolor: 'rgba(239, 68, 68, 0.08)',
            border: '1px solid rgba(239, 68, 68, 0.2)',
            color: '#ef4444'
          }}
        >
          <AlertTitle sx={{ fontWeight: 800, fontSize: '12px' }}>SCHEMA VALIDATION ERRORS</AlertTitle>
          <Box component="ul" sx={{ m: 0, pl: 2, fontSize: '11px', lineHeight: 1.5 }}>
            {validation.errors.map((err, i) => (
              <li key={i}>{err}</li>
            ))}
          </Box>
        </Alert>
      )}

      {hasWarnings && (
        <Alert
          severity="warning"
          icon={<WarningAmberIcon />}
          sx={{
            borderRadius: 2,
            bgcolor: 'rgba(245, 158, 11, 0.08)',
            border: '1px solid rgba(245, 158, 11, 0.2)',
            color: '#f59e0b'
          }}
        >
          <AlertTitle sx={{ fontWeight: 800, fontSize: '12px' }}>SCHEMA VALIDATION WARNINGS</AlertTitle>
          <Box component="ul" sx={{ m: 0, pl: 2, fontSize: '11px', lineHeight: 1.5 }}>
            {validation.warnings.map((warn, i) => (
              <li key={i}>{warn}</li>
            ))}
          </Box>
        </Alert>
      )}
    </Box>
  );
};
