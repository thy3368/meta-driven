package com.tanggo.fund.metadriven.lwc.dobject.atom.test;



import com.tanggo.fund.metadriven.lwc.dobject.atom.DClass;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DMethod;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DProperty;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DObject;

import java.util.ArrayList;
import java.util.List;

public class Test {

    void abc() {

        DClass mclass = new DClass();
        mclass.setName("entity-user");
        DObject dObject2 = mclass.createObject();

        DObject dObject = new DObject();
        dObject.setDclass(mclass);

        DObject inputs = new DObject();

        Object object = dObject.callMethod("func2", inputs);
        dObject.setValue("key", 3);
        int num = (int) dObject.getValue("key");

        Object object2 = mclass.callStaticMethod("func2", inputs);


    }

    //建模 entity
    void abc2() {

        DClass mclass = new DClass();
        mclass.setName("OrderHeader");


        //简单类型
        DProperty amount = new DProperty();
        amount.setName("amount");
        amount.setJavaType(Integer.class);

        DProperty userId = new DProperty();
        userId.setName("userId");
        userId.setJavaType(String.class);

        //集合类型
        DProperty address = new DProperty();
        address.setName("addresses");
        address.setCollectionType(ArrayList.class);
        address.setJavaType(String.class);

        List<String> addresses = new ArrayList<>();
        addresses.add("address1");
        addresses.add("address2");

        mclass.getPropertyList().add(userId);

        DObject dObject = mclass.createObject();
        //验证setValue
        dObject.setValue("amount", 123);
        dObject.setValue("userId", "userId");
        dObject.setValue("addresses", addresses);

        int amount222 = (int) dObject.getValue("amount");
        String userId2 = (String) dObject.getValue("userId");
        addresses = (List<String>) dObject.getValue("addresses");


    }

    void abc3() {

        DClass itemClass = new DClass();
        itemClass.setName("item");


        DClass header = new DClass();
        header.setName("OrderHeader");
        DProperty item = new DProperty();
        item.setName("orderItems");
        item.setCollectionType(ArrayList.class);
        item.setJavaType(DObject.class);
        item.setDynamicObjectType(itemClass);

        DObject dObject = header.createObject();
        List<DObject> items = (List<DObject>) dObject.getValue("orderItems");


        //验证 setValue的验证
        dObject.setValue("orderItems", items);


    }

    void method() {

        DClass mclass = new DClass();
        mclass.setName("OrderHeader");

        mclass.callStaticMethod("func2", null);
        DMethod method = mclass.getMethod("func2");
        Class xclass = method.getInput();
        method.inputIsDynamic();


    }

}
