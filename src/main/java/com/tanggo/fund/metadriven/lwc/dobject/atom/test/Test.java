package com.tanggo.fund.metadriven.lwc.dobject.atom.test;



import com.tanggo.fund.metadriven.lwc.dobject.atom.DClass;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DMethod;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DProperty;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DynamicObject;

import java.util.ArrayList;
import java.util.List;

public class Test {

    void abc() {

        DClass mclass = new DClass();
        mclass.setName("entity-user");
        DynamicObject dynamicObject2 = mclass.createObject();

        DynamicObject dynamicObject = new DynamicObject();
        dynamicObject.setDclass(mclass);

        DynamicObject inputs = new DynamicObject();

        Object object = dynamicObject.callMethod("func2", inputs);
        dynamicObject.setValue("key", 3);
        int num = (int) dynamicObject.getValue("key");

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

        DynamicObject dynamicObject = mclass.createObject();
        //验证setValue
        dynamicObject.setValue("amount", 123);
        dynamicObject.setValue("userId", "userId");
        dynamicObject.setValue("addresses", addresses);

        int amount222 = (int) dynamicObject.getValue("amount");
        String userId2 = (String) dynamicObject.getValue("userId");
        addresses = (List<String>) dynamicObject.getValue("addresses");


    }

    void abc3() {

        DClass itemClass = new DClass();
        itemClass.setName("item");


        DClass header = new DClass();
        header.setName("OrderHeader");
        DProperty item = new DProperty();
        item.setName("orderItems");
        item.setCollectionType(ArrayList.class);
        item.setJavaType(DynamicObject.class);
        item.setDynamicObjectType(itemClass);

        DynamicObject dynamicObject = header.createObject();
        List<DynamicObject> items = (List<DynamicObject>) dynamicObject.getValue("orderItems");


        //验证 setValue的验证
        dynamicObject.setValue("orderItems", items);


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
