import React, { useState } from 'react';
import {
  Box, Container, Typography, Button, Card, CardContent,
  IconButton, TextField, Chip, Divider, Tooltip, Grid, Paper
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CodeIcon from '@mui/icons-material/Code';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import ErrorIcon from '@mui/icons-material/Error';
import DataObjectIcon from '@mui/icons-material/DataObject';
import FlashOnIcon from '@mui/icons-material/FlashOn';
import HelpIcon from '@mui/icons-material/Help';
import { useWorkflowStore } from '../store/workflowStore';

interface RuleHelpPageProps {
  onShowNotification: (message: string, severity: 'success' | 'error') => void;
}

interface SpelExample {
  title: string;
  description: string;
  expression: string;
  mockContext: string;
}

export const RuleHelpPage: React.FC<RuleHelpPageProps> = ({ onShowNotification }) => {
  const { goBack } = useWorkflowStore();
  const [sandboxContext, setSandboxContext] = useState<string>(
    JSON.stringify({
      amount: 1500,
      customer: {
        name: "John Doe",
        tier: "VIP",
        age: 30,
        email: "john.doe@enterprise.com",
        active: true
      },
      roles: ["USER", "ADMIN"],
      items: ["laptop", "mouse", "keyboard"]
    }, null, 2)
  );

  const [sandboxExpression, setSandboxExpression] = useState<string>(
    "context.amount > 1000 and context.customer?.tier == 'VIP'"
  );

  const [loading, setLoading] = useState<boolean>(false);

  const [evalResult, setEvalResult] = useState<{
    success: boolean;
    result?: string;
    type?: string;
    value?: any;
    error?: string;
  } | null>(null);

  const handleRunEvaluation = async (exprOverride?: string, ctxOverride?: string) => {
    setLoading(true);
    setEvalResult(null);
    try {
      const expr = exprOverride !== undefined ? exprOverride : sandboxExpression;
      const ctxStr = ctxOverride !== undefined ? ctxOverride : sandboxContext;

      let parsedContext = {};
      try {
        parsedContext = JSON.parse(ctxStr);
      } catch (err: any) {
        throw new Error("Invalid Mock Context JSON: " + err.message);
      }

      const res = await fetch('/api/rules/evaluate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          expression: expr,
          context: parsedContext
        })
      });

      if (!res.ok) {
        throw new Error('Evaluation request failed on server');
      }

      const data = await res.json();
      setEvalResult(data);
    } catch (err: any) {
      setEvalResult({
        success: false,
        error: err.message
      });
      onShowNotification(err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleCopyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    onShowNotification("Expression copied to clipboard!", "success");
  };

  const loadExampleIntoSandbox = (example: SpelExample) => {
    setSandboxExpression(example.expression);
    setSandboxContext(example.mockContext);
    // Run evaluation right away with values
    handleRunEvaluation(example.expression, example.mockContext);
    onShowNotification(`Loaded "${example.title}" into Sandbox!`, "success");
  };

  const categories = [
    {
      title: "Context & Property Access",
      description: "How to reference fields in the workflow context. Standard fields can be accessed as properties directly.",
      examples: [
        {
          title: "Direct Map Property Access",
          description: "Access context root attributes directly. Standard format is context.key.",
          expression: "context.amount >= 500",
          mockContext: JSON.stringify({ amount: 750 }, null, 2)
        },
        {
          title: "Variable Syntax Access",
          description: "Alternative standard variable SpEL accessor syntax using prefix #.",
          expression: "#context.amount >= 500",
          mockContext: JSON.stringify({ amount: 750 }, null, 2)
        },
        {
          title: "Bracket String Map Access",
          description: "Required when context key contains special characters or spaces (e.g. 'user-age').",
          expression: "context['user-age'] >= 21",
          mockContext: JSON.stringify({ "user-age": 25 }, null, 2)
        }
      ]
    },
    {
      title: "Safe Navigation (Null-Safety)",
      description: "Prevent NullPointerExceptions when reading properties of nested structures that may be null.",
      examples: [
        {
          title: "Nested Property Check",
          description: "Uses ?. operator. If customer is null, expression evaluates to null safely rather than failing.",
          expression: "context.customer?.profile?.tier == 'VIP'",
          mockContext: JSON.stringify({ customer: { profile: { tier: "VIP" } } }, null, 2)
        },
        {
          title: "Partially Missing Payload",
          description: "Evaluates to false safely when intermediate objects are absent.",
          expression: "context.customer?.profile?.tier == 'VIP'",
          mockContext: JSON.stringify({ customer: null }, null, 2)
        }
      ]
    },
    {
      title: "Relational & Logical Operators",
      description: "Perform comparison tests and logical combinations for branching rules.",
      examples: [
        {
          title: "Multi-Condition Boolean Logic",
          description: "Uses 'and', 'or', and comparative operators to construct complex logical conditions.",
          expression: "context.amount > 1000 and context.country == 'US' and not context.isRiskBlocked",
          mockContext: JSON.stringify({ amount: 1500, country: "US", isRiskBlocked: false }, null, 2)
        },
        {
          title: "Equality & Negation Checks",
          description: "Use standard arithmetic operators or SpEL keyword equivalents (e.g. eq, ne).",
          expression: "context.status != 'REJECTED' or context.score >= 80",
          mockContext: JSON.stringify({ status: "PENDING", score: 85 }, null, 2)
        }
      ]
    },
    {
      title: "String & Regex Operations",
      description: "Invoke native Java String methods or match string values against pattern definitions.",
      examples: [
        {
          title: "Regex Pattern Matching",
          description: "Evaluates whether the string matches a regular expression pattern using the 'matches' keyword.",
          expression: "context.email matches '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$'",
          mockContext: JSON.stringify({ email: "hello@enterprise.com" }, null, 2)
        },
        {
          title: "String Methods Invocation",
          description: "Execute Java String methods like startsWith, toLowerCase, contains, or length.",
          expression: "context.companyName.toLowerCase().startsWith('atlas') and context.companyName.length() > 5",
          mockContext: JSON.stringify({ companyName: "AtlasWorkflowCorp" }, null, 2)
        }
      ]
    },
    {
      title: "Elvis & Ternary Operators",
      description: "Construct dynamic inline branching and default fallbacks for missing properties.",
      examples: [
        {
          title: "Elvis Defaulting Operator",
          description: "Use ?: operator to fallback to a default value if the preceding expression resolves to null.",
          expression: "(context.customer?.age ?: 18) >= 18",
          mockContext: JSON.stringify({ customer: { age: null } }, null, 2)
        },
        {
          title: "Inline Ternary Logic",
          description: "Evaluates standard conditional statements in the format: condition ? trueVal : falseVal.",
          expression: "context.score >= 90 ? 'Tier-A' : 'Tier-B'",
          mockContext: JSON.stringify({ score: 92 }, null, 2)
        }
      ]
    },
    {
      title: "Collections & Array Operators",
      description: "Inspect collection sizes, index references, or verify element presence in lists.",
      examples: [
        {
          title: "List Size & Integrity Check",
          description: "Safely verify that a collection contains elements before invoking evaluation rules.",
          expression: "context.items != null and context.items?.size() > 0",
          mockContext: JSON.stringify({ items: ["laptop", "mouse"] }, null, 2)
        },
        {
          title: "List Contains Element",
          description: "Expose standard java.util.Collection methods like contains to check element presence.",
          expression: "context.roles != null and context.roles.contains('ADMIN')",
          mockContext: JSON.stringify({ roles: ["USER", "SUPPORT", "ADMIN"] }, null, 2)
        }
      ]
    }
  ];

  const accentColor = '#8b5cf6';

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '92vh', py: 4, color: 'text.primary', transition: 'background-color 0.25s ease-in-out' }}>
      <Container maxWidth="xl">
        {/* Title bar */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
          <IconButton onClick={() => goBack()} sx={{ color: accentColor, border: `1px solid rgba(139,92,246,0.2)` }}>
            <ArrowBackIcon />
          </IconButton>
          <Box>
            <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.5px' }}>
              Spring Expression Language (SpEL) Guide
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Exhaustive syntax cheatsheet and interactive sandbox to prototype complex routing rules.
            </Typography>
          </Box>
        </Box>

        <Grid container spacing={4}>
          {/* Left Panel: Simulator / Sandbox */}
          <Grid size={{ xs: 12, lg: 6 }}>
            <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column', bgcolor: 'background.paper', position: 'relative', overflow: 'visible' }}>
              {/* Decorative background glow */}
              <Box sx={{
                position: 'absolute', top: -10, right: -10, width: 80, height: 80,
                background: 'radial-gradient(circle, rgba(139,92,246,0.15) 0%, rgba(0,0,0,0) 70%)',
                zIndex: 0, pointerEvents: 'none'
              }} />

              <CardContent sx={{ p: 3, flexGrow: 1, display: 'flex', flexDirection: 'column', gap: 2.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <FlashOnIcon sx={{ color: accentColor }} />
                  <Typography variant="h5" sx={{ fontWeight: 700 }}>
                    Interactive SpEL Sandbox
                  </Typography>
                </Box>
                <Typography variant="body2" color="text.secondary">
                  Configure a simulated JSON payload context on the left and test how SpEL expressions evaluate against it in real time.
                </Typography>

                <Grid container spacing={2} sx={{ flexGrow: 1 }}>
                  {/* Mock Context JSON Box */}
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, mb: 1 }}>
                      <DataObjectIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                      <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: '0.5px' }}>
                        MOCK CONTEXT PAYLOAD (JSON)
                      </Typography>
                    </Box>
                    <TextField
                      multiline
                      rows={14}
                      fullWidth
                      value={sandboxContext}
                      onChange={(e) => setSandboxContext(e.target.value)}
                      sx={{
                        '& .MuiInputBase-root': {
                          fontFamily: 'monospace',
                          fontSize: '13px',
                          bgcolor: 'rgba(0,0,0,0.2)',
                          border: '1px solid rgba(255,255,255,0.05)',
                          borderRadius: 2
                        }
                      }}
                    />
                  </Grid>

                  {/* Expression Input & Evaluation Output */}
                  <Grid size={{ xs: 12, sm: 6 }} sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
                      <Box>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, mb: 1 }}>
                          <CodeIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                          <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: '0.5px' }}>
                            SPEL EXPRESSION TO EVALUATE
                          </Typography>
                        </Box>
                        <TextField
                          fullWidth
                          value={sandboxExpression}
                          onChange={(e) => setSandboxExpression(e.target.value)}
                          placeholder="e.g. context.amount > 100"
                          sx={{
                            '& .MuiInputBase-root': {
                              fontFamily: 'monospace',
                              fontSize: '13px',
                              bgcolor: 'rgba(0,0,0,0.2)',
                              border: '1px solid rgba(255,255,255,0.05)',
                              borderRadius: 2
                            }
                          }}
                        />
                      </Box>

                      <Button
                        variant="contained"
                        onClick={() => handleRunEvaluation()}
                        disabled={loading}
                        startIcon={<PlayArrowIcon />}
                        sx={{
                          background: `linear-gradient(135deg, ${accentColor} 0%, #6d28d9 100%)`,
                          boxShadow: `0 4px 14px rgba(139,92,246,0.3)`,
                          alignSelf: 'flex-start',
                          py: 1, px: 3
                        }}
                      >
                        {loading ? 'Evaluating...' : 'Run Evaluation'}
                      </Button>

                      <Divider sx={{ my: 1, borderColor: 'rgba(255,255,255,0.06)' }} />

                      {/* Result panel */}
                      <Box sx={{ flexGrow: 1 }}>
                        <Typography variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', letterSpacing: '0.5px', display: 'block', mb: 1 }}>
                          EVALUATION SIMULATION RESULT
                        </Typography>

                        {evalResult ? (
                          <Paper sx={{
                            p: 2.5,
                            borderRadius: 2.5,
                            bgcolor: 'rgba(0,0,0,0.15)',
                            border: '1px solid',
                            borderColor: evalResult.success ? 'rgba(34, 197, 94, 0.2)' : 'rgba(239, 68, 68, 0.2)',
                            minHeight: 120,
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: 'center'
                          }}>
                            {evalResult.success ? (
                              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                  <CheckCircleOutlinedIcon sx={{ color: '#22c55e' }} />
                                  <Typography variant="subtitle2" sx={{ color: '#22c55e', fontWeight: 800 }}>
                                    Evaluation Successful
                                  </Typography>
                                </Box>
                                <Box>
                                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                                    RETURN VALUE
                                  </Typography>
                                  <Typography variant="body1" sx={{ fontFamily: 'monospace', fontWeight: 700, color: '#a78bfa', fontSize: '15px', wordBreak: 'break-all' }}>
                                    {String(evalResult.value)}
                                  </Typography>
                                </Box>
                                <Box sx={{ display: 'flex', gap: 2 }}>
                                  <Box>
                                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                                      RESULT TYPE
                                    </Typography>
                                    <Chip label={evalResult.type} size="small" sx={{ fontWeight: 800, fontSize: '10px', bgcolor: 'rgba(255,255,255,0.06)' }} />
                                  </Box>
                                  <Box>
                                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                                      AS STRING
                                    </Typography>
                                    <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'text.secondary', fontSize: '12px' }}>
                                      "{evalResult.result}"
                                    </Typography>
                                  </Box>
                                </Box>
                              </Box>
                            ) : (
                              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                  <ErrorIcon sx={{ color: '#ef4444' }} />
                                  <Typography variant="subtitle2" sx={{ color: '#ef4444', fontWeight: 800 }}>
                                    Evaluation Error
                                  </Typography>
                                </Box>
                                <Typography variant="body2" sx={{ fontFamily: 'monospace', color: '#fca5a5', fontSize: '12.5px', mt: 1, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                                  {evalResult.error}
                                </Typography>
                              </Box>
                            )}
                          </Paper>
                        ) : (
                          <Box sx={{
                            display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                            p: 4, border: '1px dashed', borderColor: 'divider', borderRadius: 2.5, minHeight: 120, textAlign: 'center'
                          }}>
                            <HelpIcon sx={{ fontSize: 28, color: 'text.secondary', mb: 1, opacity: 0.5 }} />
                            <Typography variant="body2" color="text.secondary">
                              Expression results will appear here. Click "Run Evaluation" above.
                            </Typography>
                          </Box>
                        )}
                      </Box>
                    </Box>
                  </Grid>
                </Grid>
              </CardContent>
            </Card>
          </Grid>

          {/* Right Panel: Syntax Reference Cheat Sheet */}
          <Grid size={{ xs: 12, lg: 6 }}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              {categories.map((cat, catIdx) => (
                <Card key={catIdx} sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
                  <CardContent sx={{ p: 3 }}>
                    <Typography variant="h6" sx={{ fontWeight: 800, mb: 0.5, color: '#f3f4f6' }}>
                      {cat.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2.5 }}>
                      {cat.description}
                    </Typography>

                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                      {cat.examples.map((ex, exIdx) => (
                        <Paper key={exIdx} sx={{
                          p: 2, borderRadius: 2, bgcolor: 'rgba(255,255,255,0.01)',
                          border: '1px solid rgba(255,255,255,0.03)',
                          '&:hover': { bgcolor: 'rgba(255,255,255,0.02)' }
                        }}>
                          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 700, color: accentColor }}>
                              {ex.title}
                            </Typography>
                            <Box sx={{ display: 'flex', gap: 1 }}>
                              <Tooltip title="Try in Sandbox" arrow>
                                <Button
                                  variant="outlined"
                                  size="small"
                                  onClick={() => loadExampleIntoSandbox(ex)}
                                  sx={{
                                    py: 0.3, px: 1, minWidth: 0, fontSize: '10px', fontWeight: 800, height: 22,
                                    borderColor: 'rgba(139,92,246,0.3)', color: accentColor,
                                    '&:hover': { borderColor: accentColor, bgcolor: 'rgba(139,92,246,0.06)' }
                                  }}
                                >
                                  Sandbox
                                </Button>
                              </Tooltip>
                              <Tooltip title="Copy Expression" arrow>
                                <IconButton
                                  size="small"
                                  onClick={() => handleCopyToClipboard(ex.expression)}
                                  sx={{ color: 'text.secondary', width: 22, height: 22 }}
                                >
                                  <ContentCopyIcon sx={{ fontSize: 13 }} />
                                </IconButton>
                              </Tooltip>
                            </Box>
                          </Box>
                          <Typography variant="body2" color="text.secondary" sx={{ fontSize: '12.5px', mb: 1.5 }}>
                            {ex.description}
                          </Typography>
                          <Box sx={{ bgcolor: 'rgba(0,0,0,0.2)', p: 1.2, borderRadius: 1.2, display: 'flex', alignItems: 'center' }}>
                            <CodeIcon sx={{ fontSize: 13, color: 'text.secondary', mr: 1 }} />
                            <Typography variant="body2" sx={{ fontFamily: 'monospace', color: '#a78bfa', fontSize: '12.5px', overflowX: 'auto' }}>
                              {ex.expression}
                            </Typography>
                          </Box>
                        </Paper>
                      ))}
                    </Box>
                  </CardContent>
                </Card>
              ))}
            </Box>
          </Grid>
        </Grid>
      </Container>
    </Box>
  );
};
