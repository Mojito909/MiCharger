# MiCharger R8 规则
# 项目自身无反射，Compose / Room / DataStore 均自带 consumer 规则；
# libsu 通过壳进程交互，保守保留完整包避免混淆破坏 root 调用。

-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**
