import { Button } from '@mui/material';
import ConstructionIcon from '@mui/icons-material/HandymanOutlined';
import { Link } from 'react-router-dom';
import EmptyState from './EmptyState';
import PageContainer from './PageContainer';

export default function ComingSoon({ title, description }: { title: string; description: string }) {
  return (
    <PageContainer maxWidth="md">
      <EmptyState icon={<ConstructionIcon />} title={title} description={description}
        action={<Button component={Link} to="/profile" variant="outlined">Complete your profile meanwhile</Button>} />
    </PageContainer>
  );
}
