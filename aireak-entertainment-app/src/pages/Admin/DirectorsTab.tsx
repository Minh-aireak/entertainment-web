import React from 'react';
import { filmService } from '../../api/filmService';
import PersonManagementTab from './PersonManagementTab';

const DirectorsTab: React.FC = () => (
  <PersonManagementTab
    addLabel="Thêm đạo diễn"
    createTitle="Thêm đạo diễn mới"
    editTitle="Chỉnh sửa đạo diễn"
    fetchPage={(page, size) => filmService.getAllDirectors(page, size)}
    create={(data) => filmService.createDirector(data)}
    update={(id, data) => filmService.updateDirector(id, data)}
    remove={(id) => filmService.deleteDirector(id)}
    deleteConfirmMessage={(name) => `Bạn có chắc muốn xóa đạo diễn "${name}"?`}
  />
);

export default DirectorsTab;
