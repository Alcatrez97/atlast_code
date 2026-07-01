import { createTheme } from '@mui/material/styles';
import type { Theme } from '@mui/material/styles';

export type ThemeMode =
  | 'vi-light'
  | 'vi-dark'
  | 'cosmos-dark'
  | 'slate-dark'
  | 'nord-light'
  | 'emerald-light'
  | 'neural-dark'
  | 'cyberpunk-dark'
  | 'sunset-dark'
  | 'ruby-dark'
  | 'ocean-light'
  | 'lavender-light'
  | 'coffee-dark';

export const getTheme = (mode: ThemeMode): Theme => {
  const isDark = mode.endsWith('dark');
  let primaryMain = '#6366f1'; // Indigo default
  let secondaryMain = '#14b8a6'; // Teal default
  let defaultBg = '#070a13'; // Deep cosmos blue-black
  let paperBg = '#0f1424'; // Dark blue-slate card paper
  let textPrimary = '#f3f4f6';
  let textSecondary = '#9ca3af';

  if (mode === 'vi-light') {
    primaryMain = '#5E2750'; // Vi Aubergine
    secondaryMain = '#E60000'; // Vi Electric Red (Vodafone)
    defaultBg = '#FAF9FA'; // Clean, soft warm-white background
    paperBg = '#FFFFFF';
    textPrimary = '#1E1E1E';   // Off-black typography
    textSecondary = '#4A4D4E'; // Abbey Charcoal
  }

  else if (mode === 'vi-dark') {
    primaryMain = '#FECB00'; // Vi Vibrant Yellow (Idea)
    secondaryMain = '#E60000'; // Vi Electric Red (Vodafone)
    defaultBg = '#1E0016'; // Deep aubergine-black
    paperBg = '#2D0623'; // Dark aubergine purple
    textPrimary = '#FFFFFF';
    textSecondary = '#E6D3E0';
  }

  else if (mode === 'slate-dark') {
    primaryMain = '#3b82f6';
    secondaryMain = '#10b981';
    defaultBg = '#0f172a';
    paperBg = '#1e293b';
    textPrimary = '#f8fafc';
    textSecondary = '#94a3b8';
  }

  else if (mode === 'nord-light') {
    primaryMain = '#aba7fc48';
    secondaryMain = '#0ea5e9';
    defaultBg = '#f8fafc';
    paperBg = '#ffffff';
    textPrimary = '#0f172a';
    textSecondary = '#475569';
  }

  else if (mode === 'emerald-light') {
    primaryMain = '#059669';
    secondaryMain = '#8b5cf6';
    defaultBg = '#f4f6f4';
    paperBg = '#ffffff';
    textPrimary = '#0f172a';
    textSecondary = '#475569';
  }

  else if (mode === 'neural-dark') {
    primaryMain = '#6366f1';
    secondaryMain = '#06b6d4';
    defaultBg = '#020617';
    paperBg = '#0f172a';
    textPrimary = '#f8fafc';
    textSecondary = '#94a3b8';
  }

  else if (mode === 'cyberpunk-dark') {
    primaryMain = '#00f5d4';
    secondaryMain = '#ff006e';
    defaultBg = '#0a0a0f';
    paperBg = '#151520';
    textPrimary = '#ffffff';
    textSecondary = '#94a3b8';
  }

  else if (mode === 'sunset-dark') {
    primaryMain = '#f97316';
    secondaryMain = '#ec4899';
    defaultBg = '#1c1917';
    paperBg = '#292524';
    textPrimary = '#fafaf9';
    textSecondary = '#a8a29e';
  }

  else if (mode === 'ruby-dark') {
    primaryMain = '#dc2626';
    secondaryMain = '#f59e0b';
    defaultBg = '#0f0a0a';
    paperBg = '#1c1111';
    textPrimary = '#fef2f2';
    textSecondary = '#fca5a5';
  }

  else if (mode === 'ocean-light') {
    primaryMain = '#0284c7';
    secondaryMain = '#06b6d4';
    defaultBg = '#f0f9ff';
    paperBg = '#ffffff';
    textPrimary = '#082f49';
    textSecondary = '#475569';
  }

  else if (mode === 'lavender-light') {
    primaryMain = '#8b5cf6';
    secondaryMain = '#ec4899';
    defaultBg = '#faf5ff';
    paperBg = '#ffffff';
    textPrimary = '#2e1065';
    textSecondary = '#6d28d9';
  }

  else if (mode === 'coffee-dark') {
    primaryMain = '#d97706';
    secondaryMain = '#92400e';
    defaultBg = '#1c1917';
    paperBg = '#292524';
    textPrimary = '#fafaf9';
    textSecondary = '#d6d3d1';
  }

  return createTheme({
    palette: {
      mode: isDark ? 'dark' : 'light',
      primary: {
        main: primaryMain,
      },
      secondary: {
        main: secondaryMain,
      },
      background: {
        default: defaultBg,
        paper: paperBg,
      },
      text: {
        primary: textPrimary,
        secondary: textSecondary,
      },
      divider: isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.08)',
    },
    typography: {
      fontFamily: mode.startsWith('vi-')
        ? '"Inter", "Roboto", "Helvetica", "Arial", sans-serif'
        : '"Plus Jakarta Sans", "Roboto", "Helvetica", "Arial", sans-serif',
      h1: {
        fontFamily: mode.startsWith('vi-') ? '"Inter", sans-serif' : '"Outfit", sans-serif',
        fontWeight: 700,
      },
      h2: {
        fontFamily: mode.startsWith('vi-') ? '"Inter", sans-serif' : '"Outfit", sans-serif',
        fontWeight: 700,
      },
      h3: {
        fontFamily: mode.startsWith('vi-') ? '"Inter", sans-serif' : '"Outfit", sans-serif',
        fontWeight: 600,
      },
      h4: {
        fontFamily: mode.startsWith('vi-') ? '"Inter", sans-serif' : '"Outfit", sans-serif',
        fontWeight: 600,
      },
      h5: {
        fontFamily: mode.startsWith('vi-') ? '"Inter", sans-serif' : '"Outfit", sans-serif',
        fontWeight: 600,
      },
      h6: {
        fontFamily: mode.startsWith('vi-') ? '"Inter", sans-serif' : '"Outfit", sans-serif',
        fontWeight: 600,
      },
      subtitle1: {
        fontFamily: mode.startsWith('vi-') ? '"Inter", sans-serif' : '"Plus Jakarta Sans", sans-serif',
        fontWeight: 600,
      },
      body1: {
        fontFamily: mode.startsWith('vi-') ? '"Inter", sans-serif' : '"Plus Jakarta Sans", sans-serif',
        lineHeight: 1.6,
      },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            backgroundColor: defaultBg,
            color: textPrimary,
            transition: 'background-color 0.25s ease-in-out, color 0.25s ease-in-out',
          },
        },
      },
      MuiButton: {
        styleOverrides: {
          root: {
            borderRadius: mode.startsWith('vi-') ? 24 : 8,
            textTransform: mode.startsWith('vi-') ? 'lowercase' : 'none',
            fontWeight: 700,
            padding: mode.startsWith('vi-') ? '8px 24px' : '8px 16px',
            transition: 'all 0.2s ease-in-out',
            '&:hover': {
              transform: 'translateY(-1px)',
              boxShadow: isDark
                ? `0 4px 12px ${primaryMain}4D`
                : `0 4px 12px ${primaryMain}33`,
            },
            '&.MuiButton-containedSecondary:hover': {
              boxShadow: isDark
                ? `0 4px 12px ${secondaryMain}4D`
                : `0 4px 12px ${secondaryMain}33`,
            },
          },
        },
      },
      MuiTab: {
        styleOverrides: {
          root: {
            textTransform: mode.startsWith('vi-') ? 'lowercase' : 'none',
            fontWeight: 700,
            '&.Mui-selected': {
              color: mode.startsWith('vi-') ? '#E60000' : undefined,
            },
          },
        },
      },
      MuiTabs: {
        styleOverrides: {
          indicator: {
            backgroundColor: mode.startsWith('vi-') ? '#E60000' : undefined,
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 16,
            backgroundImage: isDark
              ? 'linear-gradient(to bottom right, rgba(255,255,255,0.03), rgba(255,255,255,0.01))'
              : 'linear-gradient(to bottom right, rgba(0,0,0,0.01), rgba(0,0,0,0.02))',
            backdropFilter: 'blur(10px)',
            border: isDark
              ? '1px solid rgba(255, 255, 255, 0.05)'
              : '1px solid rgba(0, 0, 0, 0.05)',
            boxShadow: isDark
              ? '0 8px 32px 0 rgba(0, 0, 0, 0.4)'
              : '0 8px 32px 0 rgba(0, 0, 0, 0.05)',
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: 16,
            backgroundColor: paperBg,
            border: isDark
              ? '1px solid rgba(255, 255, 255, 0.08)'
              : '1px solid rgba(0, 0, 0, 0.08)',
            backgroundImage: 'none',
          },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          root: {
            borderBottom: isDark
              ? '1px solid rgba(255, 255, 255, 0.05)'
              : '1px solid rgba(0, 0, 0, 0.05)',
          },
          head: {
            fontWeight: 600,
            color: textSecondary,
          },
        },
      },
    },
  });
};
