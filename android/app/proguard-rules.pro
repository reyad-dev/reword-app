# Proguard rules for ReWord
-keep class com.reword.app.model.** { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !static !transient <methods>;
    !private <methods>;
}
