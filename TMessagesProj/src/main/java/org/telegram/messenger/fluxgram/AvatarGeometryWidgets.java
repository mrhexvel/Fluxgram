package org.telegram.messenger.fluxgram;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatsWidgetProvider;
import org.telegram.messenger.ContactsWidgetProvider;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;

public final class AvatarGeometryWidgets {
    private static final int AVATAR_SIZE_DP = 48;

    public static Bitmap render(
            int account,
            long dialogId,
            TLRPC.User user,
            TLRPC.Chat chat,
            Bitmap photo
    ) {
        int size = AndroidUtilities.dp(AVATAR_SIZE_DP);
        int stockRadius = size / 2;
        int radius = isEligible(dialogId, user, chat)
                ? AvatarGeometry.resolveRadius(account, size, AvatarGeometry.ROLE_WIDGET, stockRadius)
                : stockRadius;

        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        result.eraseColor(Color.TRANSPARENT);
        Canvas canvas = new Canvas(result);

        if (photo == null) {
            AvatarDrawable avatarDrawable;
            if (user != null) {
                avatarDrawable = new AvatarDrawable(user);
                if (UserObject.isReplyUser(user)) {
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                } else if (UserObject.isUserSelf(user)) {
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                }
            } else {
                avatarDrawable = new AvatarDrawable();
                avatarDrawable.setInfo(account, chat);
            }
            avatarDrawable.setRoundRadius(radius);
            avatarDrawable.setBounds(0, 0, size, size);
            avatarDrawable.draw(canvas);
        } else {
            BitmapShader shader = new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float scale = Math.max(size / (float) photo.getWidth(), size / (float) photo.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate(
                    (size - photo.getWidth() * scale) / 2f,
                    (size - photo.getHeight() * scale) / 2f
            );
            shader.setLocalMatrix(matrix);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setShader(shader);
            canvas.drawRoundRect(new RectF(0, 0, size, size), radius, radius, paint);
        }

        canvas.setBitmap(null);
        return result;
    }

    public static void refresh(int account) {
        Context context = ApplicationLoader.applicationContext;
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        SharedPreferences preferences = context.getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE);

        refreshProvider(context, manager, preferences, account, ChatsWidgetProvider.class);
        refreshProvider(context, manager, preferences, account, ContactsWidgetProvider.class);
    }

    private static void refreshProvider(
            Context context,
            AppWidgetManager manager,
            SharedPreferences preferences,
            int account,
            Class<?> provider
    ) {
        int[] widgetIds = manager.getAppWidgetIds(new ComponentName(context, provider));
        for (int widgetId : widgetIds) {
            if (preferences.getInt("account" + widgetId, -1) == account
                    && !preferences.getBoolean("deleted" + widgetId, false)) {
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.list_view);
            }
        }
    }

    private static boolean isEligible(long dialogId, TLRPC.User user, TLRPC.Chat chat) {
        if (DialogObject.isUserDialog(dialogId)) {
            return user != null
                    && user.id > 0
                    && !UserObject.isDeleted(user)
                    && !UserObject.isUserSelf(user)
                    && !UserObject.isReplyUser(user)
                    && !UserObject.isAnonymous(user)
                    && !UserObject.isService(user.id)
                    && user.id != UserObject.VERIFY
                    && user.id != UserObject.OAUTH;
        }
        return DialogObject.isChatDialog(dialogId)
                && chat != null
                && chat.id > 0;
    }

    private AvatarGeometryWidgets() {
    }
}
