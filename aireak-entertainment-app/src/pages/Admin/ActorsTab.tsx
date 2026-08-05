import React from 'react';
import { filmService } from '../../api/filmService';
import PersonManagementTab from './PersonManagementTab';

const ActorsTab: React.FC = () => (
  <PersonManagementTab
    addLabel="Thêm diễn viên"
    createTitle="Thêm diễn viên mới"
    editTitle="Chỉnh sửa diễn viên"
    fetchPage={(page, size) => filmService.getAllActors(page, size)}
    create={(data) => filmService.createActor(data)}
    update={(id, data) => filmService.updateActor(id, data)}
    remove={(id) => filmService.deleteActor(id)}
    deleteConfirmMessage={(name) => `Bạn có chắc muốn xóa diễn viên "${name}"?`}
  />
);

export default ActorsTab;
