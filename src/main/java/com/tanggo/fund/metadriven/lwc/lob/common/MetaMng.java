package com.tanggo.fund.metadriven.lwc.lob.common;

import com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait.IEntityMetaRepo;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MetaMng {


    @Autowired
    private OtherEntityMetaRepo otherEntityMetaRepo;
    @Autowired
    private IEntityMetaRepo entityMetaRepo;


    /**
     * 导入元数据
     */
    public void importData() {

        List<DClass> dClasses = new ArrayList<>();
        entityMetaRepo.insertBatch(dClasses);

    }

    /**
     * 导出源数据
     */
    public void exportData() {


        List<DClass> dClasses = entityMetaRepo.query();
        List<Object> objects = new ArrayList<>();
        otherEntityMetaRepo.insertBatch(objects);

    }


}
