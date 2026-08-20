import { createTheme } from '@mui/material/styles';
// Enterprise Design Tokens
export const tokens = {
    colors: {
        brand: {
            crimson: {
                main: '#EE2737',
                80: '#F3525F',
                60: '#F57D87',
                40: '#F8A9AF',
                20: '#FCD4D7',
            },
            mustard: {
                main: '#FFC600',
                80: '#FFD133',
                60: '#FFDD66',
                40: '#FFE899',
                20: '#FFF4CC',
            },
        },
        slate: {
            100: '#2F3043',
            80: '#595969',
            60: '#82838E',
            40: '#ACACB4',
            20: '#D5D6D9',
            10: '#EAEAEC',
            5: '#F4F4F4',
        },
        accent: {
            sky: '#0CADEF',
            terracotta: '#FF8E75',
            lime: '#69EF00',
            blue: '#00A9FF',
            rose: '#F9E8D0',
            purple: '#5F004B',
        },
        status: {
            success: '#00A05A',
            warning: '#DF720E',
            error: '#B30E0E',
        },
        basic: {
            black: '#111622',
            white: '#FFFFFF',
        },
        background: {
            appGradientFrom: '#F4F4F4',
            appGradientTo: '#F4F4F4',
        }
    },
    typography: {
        fontFamily: '"Inter", "Helvetica", "Arial", sans-serif',
    },
    shape: {
        borderRadius: 12,
    }
};
export const getTheme = (_mode = 'brand-light') => {
    return createTheme({
        spacing: 8, // 8px design system
        shape: {
            borderRadius: tokens.shape.borderRadius,
        },
        palette: {
            mode: 'light',
            primary: {
                main: tokens.colors.brand.crimson.main,
                light: tokens.colors.brand.crimson[80],
                dark: '#CC1E2C', // Slightly darker for hover states
                contrastText: tokens.colors.basic.white,
            },
            secondary: {
                main: tokens.colors.slate[100],
                light: tokens.colors.slate[80],
                contrastText: tokens.colors.basic.white,
            },
            error: {
                main: tokens.colors.status.error,
            },
            warning: {
                main: tokens.colors.status.warning,
            },
            info: {
                main: tokens.colors.accent.sky,
            },
            success: {
                main: tokens.colors.status.success,
            },
            background: {
                default: tokens.colors.background.appGradientFrom,
                paper: tokens.colors.basic.white,
            },
            text: {
                primary: tokens.colors.slate[100],
                secondary: tokens.colors.slate[80],
            },
            divider: tokens.colors.slate[10],
        },
        typography: {
            fontFamily: tokens.typography.fontFamily,
            h1: { fontSize: '32px', fontWeight: 700, color: tokens.colors.slate[100], letterSpacing: '-0.02em' },
            h2: { fontSize: '24px', fontWeight: 600, color: tokens.colors.slate[100], letterSpacing: '-0.01em' },
            h3: { fontSize: '20px', fontWeight: 600, color: tokens.colors.slate[100] },
            h4: { fontSize: '18px', fontWeight: 600, color: tokens.colors.slate[100] },
            h5: { fontSize: '16px', fontWeight: 600, color: tokens.colors.slate[100] },
            h6: { fontSize: '14px', fontWeight: 600, color: tokens.colors.slate[100] },
            body1: { fontSize: '14px', color: tokens.colors.slate[100] },
            body2: { fontSize: '13px', color: tokens.colors.slate[80] },
            button: { textTransform: 'none', fontWeight: 600 },
        },
        components: {
            MuiCssBaseline: {
                styleOverrides: `
          body {
            background: linear-gradient(135deg, ${tokens.colors.background.appGradientFrom} 0%, ${tokens.colors.background.appGradientTo} 100%);
            background-attachment: fixed;
            color: ${tokens.colors.slate[100]};
          }
        `,
            },
            MuiButton: {
                styleOverrides: {
                    root: {
                        textTransform: 'none',
                        borderRadius: '6px',
                        padding: '8px 16px',
                        boxShadow: 'none',
                        fontWeight: 500,
                        transition: 'all 150ms ease-in-out',
                        '&:hover': {
                            boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                        },
                    }
                },
            },
            MuiCard: {
                styleOverrides: {
                    root: {
                        borderRadius: tokens.shape.borderRadius,
                        boxShadow: '0 4px 12px rgba(47, 48, 67, 0.04), 0 1px 3px rgba(47, 48, 67, 0.02)',
                        border: '1px solid ' + tokens.colors.slate[10],
                        backgroundImage: 'none',
                        backgroundColor: tokens.colors.basic.white,
                        padding: '24px',
                        transition: 'box-shadow 150ms ease-in-out, transform 150ms ease-in-out',
                        '&:hover': {
                            boxShadow: '0 8px 24px rgba(47, 48, 67, 0.06), 0 2px 6px rgba(47, 48, 67, 0.04)',
                        }
                    },
                },
            },
            MuiPaper: {
                styleOverrides: {
                    root: {
                        backgroundImage: 'none',
                    }
                }
            },
            MuiTableCell: {
                styleOverrides: {
                    root: {
                        borderBottom: '1px solid ' + tokens.colors.slate[10],
                        padding: '12px 16px',
                        fontSize: '13px',
                        color: tokens.colors.slate[100],
                    },
                    head: {
                        fontWeight: 600,
                        color: tokens.colors.slate[80],
                        backgroundColor: tokens.colors.slate[5],
                        textTransform: 'uppercase',
                        fontSize: '11px',
                        letterSpacing: '0.05em',
                    },
                },
            },
            MuiTableRow: {
                styleOverrides: {
                    root: {
                        transition: 'background-color 150ms ease-in-out',
                        '&:hover': {
                            backgroundColor: tokens.colors.slate[5],
                        },
                    },
                },
            },
            MuiChip: {
                styleOverrides: {
                    root: {
                        borderRadius: '4px',
                        fontWeight: 600,
                        fontSize: '12px',
                        height: '24px',
                    },
                    colorSuccess: {
                        backgroundColor: 'rgba(0, 160, 90, 0.1)',
                        color: tokens.colors.status.success,
                    },
                    colorWarning: {
                        backgroundColor: 'rgba(223, 114, 14, 0.1)',
                        color: tokens.colors.status.warning,
                    },
                    colorError: {
                        backgroundColor: 'rgba(179, 14, 14, 0.1)',
                        color: tokens.colors.status.error,
                    },
                    colorPrimary: {
                        backgroundColor: tokens.colors.brand.crimson[20],
                        color: tokens.colors.brand.crimson.main,
                    },
                    colorDefault: {
                        backgroundColor: tokens.colors.slate[10],
                        color: tokens.colors.slate[80],
                    }
                }
            },
            MuiDrawer: {
                styleOverrides: {
                    paper: {
                        backgroundColor: tokens.colors.slate[100],
                        color: tokens.colors.basic.white,
                        borderRight: 'none',
                    }
                }
            }
        },
    });
};
