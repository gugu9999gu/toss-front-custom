package dev.tossfront.record;

import android.os.Build;
import android.view.*;

final class SystemBars {
    static void apply(Window window,Appearance a){
        window.setStatusBarColor(a.bg());window.setNavigationBarColor(a.bg());
        if(Build.VERSION.SDK_INT>=30){window.setDecorFitsSystemWindows(!a.immersive);WindowInsetsController controller=window.getInsetsController();if(controller!=null){int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(a.dark()?0:light,light);controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(a.immersive)controller.hide(WindowInsets.Type.systemBars());else controller.show(WindowInsets.Type.systemBars());}}
        else window.getDecorView().setSystemUiVisibility(a.immersive?View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION:0);
    }
}
