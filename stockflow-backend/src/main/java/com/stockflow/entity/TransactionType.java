package com.stockflow.entity;
public enum TransactionType {
 STOCK_IN(1), STOCK_OUT(-1), ADJUSTMENT_IN(1), ADJUSTMENT_OUT(-1);
 private final int direction;
 TransactionType(int direction) { this.direction=direction; }
 public int direction() { return direction; }
}