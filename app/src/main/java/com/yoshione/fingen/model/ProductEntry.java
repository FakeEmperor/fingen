package com.yoshione.fingen.model;

import android.content.ContentValues;
import android.os.Parcel;

import com.yoshione.fingen.DBHelper;
import com.yoshione.fingen.interfaces.IAbstractModel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by slv on 30.01.2018.
 *
 */

public class ProductEntry extends BaseModel implements IAbstractModel {

    private long mProductID;
    private long mTransactionID;
    private long mCategoryID;
    private long mProjectID;
    private long mDepartmentID;
    private BigDecimal mPrice;
    private BigDecimal mQuantity;
    private boolean mSelected;

    public ProductEntry() {
        super();
        mProductID = -1;
        mQuantity = BigDecimal.ONE;
        mPrice = BigDecimal.ZERO;
        mCategoryID = -1;
        mProjectID = -1;
        mDepartmentID = -1;
        mTransactionID = -1;
    }

    public ProductEntry(long id, long productID, BigDecimal quantity, BigDecimal price, long categoryID, long projectID, long departmentID, long transactionID) {
        super();
        setID(id);
        mProductID = productID;
        mQuantity = quantity;
        mPrice = price;
        mCategoryID = categoryID;
        mProjectID = projectID;
        mDepartmentID = departmentID;
        mTransactionID = transactionID;
    }

    public long getProductID() {
        return mProductID;
    }

    public void setProductID(long productID) {
        mProductID = productID;
    }

    public long getTransactionID() {
        return mTransactionID;
    }

    public void setTransactionID(long transactionID) {
        mTransactionID = transactionID;
    }

    public long getCategoryID() {
        return mCategoryID;
    }

    public void setCategoryID(long categoryID) {
        mCategoryID = categoryID;
    }

    public long getProjectID() {
        return mProjectID;
    }

    public void setProjectID(long projectID) {
        mProjectID = projectID;
    }

    public long getDepartmentID() {
        return mDepartmentID;
    }

    public void setDepartmentID(long departmentID) {
        mDepartmentID = departmentID;
    }

    public BigDecimal getPrice() {
        return mPrice;
    }

    public void setPrice(BigDecimal price) {
        mPrice = price.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getQuantity() {
        return mQuantity;
    }

    public void setQuantity(BigDecimal quantity) {
        switch (quantity.compareTo(BigDecimal.ZERO)) {
            case 0:
            case 1:
                mQuantity = quantity.setScale(3, RoundingMode.HALF_UP);
                break;
            case -1:
                mQuantity = BigDecimal.ZERO;
                break;
        }
    }

    @Override
    public boolean isSelected() {
        return mSelected;
    }

    @Override
    public void setSelected(boolean selected) {
        mSelected = selected;
    }

    @Override
    public ContentValues getCV() {
        ContentValues values = super.getCV();

        values.put(DBHelper.C_LOG_PRODUCTS_TRANSACTIONID, mTransactionID);
        values.put(DBHelper.C_LOG_PRODUCTS_CATEGORY_ID, mCategoryID);
        values.put(DBHelper.C_LOG_PRODUCTS_PROJECT_ID, mProjectID);
        values.put(DBHelper.C_LOG_PRODUCTS_PRODUCTID, mProductID);
        values.put(DBHelper.C_LOG_PRODUCTS_QUANTITY, mQuantity.doubleValue());
        values.put(DBHelper.C_LOG_PRODUCTS_PRICE, mPrice.doubleValue());
        values.put(DBHelper.C_LOG_PRODUCTS_DEPARTMENT_ID, mDepartmentID);
        return values;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeLong(this.mProductID);
        dest.writeLong(this.mTransactionID);
        dest.writeLong(this.mCategoryID);
        dest.writeLong(this.mProjectID);
        dest.writeSerializable(this.mPrice);
        dest.writeSerializable(this.mQuantity);
        dest.writeByte(this.mSelected ? (byte) 1 : (byte) 0);
        dest.writeLong(this.mDepartmentID);
    }

    protected ProductEntry(Parcel in) {
        super(in);
        this.mProductID = in.readLong();
        this.mTransactionID = in.readLong();
        this.mCategoryID = in.readLong();
        this.mProjectID = in.readLong();
        this.mPrice = (BigDecimal) in.readSerializable();
        this.mQuantity = (BigDecimal) in.readSerializable();
        this.mSelected = in.readByte() != 0;
        this.mDepartmentID = in.readLong();
    }

    public static final Creator<ProductEntry> CREATOR = new Creator<ProductEntry>() {
        @Override
        public ProductEntry createFromParcel(Parcel source) {
            return new ProductEntry(source);
        }

        @Override
        public ProductEntry[] newArray(int size) {
            return new ProductEntry[size];
        }
    };
}
