import LanguageIcon from '@mui/icons-material/Language';
import { Button, Tooltip } from '@mui/material';
import type { FC } from 'react';
import { useTranslation } from 'react-i18next';

interface LanguageSwitcherProps {
  compact?: boolean;
}

const LanguageSwitcher: FC<LanguageSwitcherProps> = ({ compact = false }) => {
  const { t, i18n } = useTranslation();
  const currentLanguage = i18n.resolvedLanguage?.startsWith('en') ? 'en' : 'vi';
  const nextLanguage = currentLanguage === 'vi' ? 'en' : 'vi';

  const handleChangeLanguage = () => {
    void i18n.changeLanguage(nextLanguage);
  };

  return (
    <Tooltip title={t('switchToLanguage', { language: t(`language.${nextLanguage}`) })}>
      <Button
        onClick={handleChangeLanguage}
        aria-label={t('switchToLanguage', { language: t(`language.${nextLanguage}`) })}
        startIcon={<LanguageIcon fontSize="small" />}
        sx={{
          minWidth: compact ? 44 : 82,
          width: compact ? 44 : 'auto',
          height: 40,
          px: compact ? 0 : 1.5,
          borderRadius: 2.5,
          color: 'text.secondary',
          fontWeight: 800,
          textTransform: 'uppercase',
          '& .MuiButton-startIcon': { m: compact ? 0 : undefined },
          '&:hover': { bgcolor: 'action.hover', color: 'primary.main' },
        }}
      >
        {!compact && currentLanguage}
      </Button>
    </Tooltip>
  );
};

export default LanguageSwitcher;
