import React, { createContext, useCallback, useContext, useRef, useState } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';

interface ConfirmOptions {
  title?: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  confirmColor?: 'primary' | 'error' | 'warning' | 'success' | 'info';
}

type ConfirmFn = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmDialogContext = createContext<ConfirmFn | null>(null);

export const useConfirmDialog = (): ConfirmFn => {
  const ctx = useContext(ConfirmDialogContext);
  if (!ctx) {
    throw new Error('useConfirmDialog must be used within a ConfirmDialogProvider');
  }
  return ctx;
};

export const ConfirmDialogProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const [options, setOptions] = useState<ConfirmOptions | null>(null);
  const resolveRef = useRef<((result: boolean) => void) | null>(null);

  const confirm = useCallback<ConfirmFn>((opts) => {
    return new Promise<boolean>((resolve) => {
      resolveRef.current = resolve;
      setOptions(opts);
    });
  }, []);

  const handleClose = (result: boolean) => {
    setOptions(null);
    resolveRef.current?.(result);
    resolveRef.current = null;
  };

  return (
    <ConfirmDialogContext.Provider value={confirm}>
      {children}
      <Dialog open={!!options} onClose={() => handleClose(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{options?.title ?? 'Xác nhận'}</DialogTitle>
        <DialogContent>
          <DialogContentText>{options?.message}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => handleClose(false)}>{options?.cancelText ?? 'Hủy'}</Button>
          <Button
            onClick={() => handleClose(true)}
            color={options?.confirmColor ?? 'error'}
            variant="contained"
            autoFocus
          >
            {options?.confirmText ?? 'Xác nhận'}
          </Button>
        </DialogActions>
      </Dialog>
    </ConfirmDialogContext.Provider>
  );
};
