package com.kapi.notes.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.kapi.notes.R

/**
 * KapiNotes' own icon set - simple rounded line-art, drawn fresh instead of using the stock
 * Material Icons, as a small but consistent brand touch across the whole app. Every icon is a
 * 24x24 vector drawable under res/drawable/ic_kapi_*.xml; this object just exposes them as
 * ImageVector so every existing call site (Icon(imageVector = ...), TooltipIconButton(icon = ...))
 * keeps working unchanged - only the Icons.Rounded.X / Icons.AutoMirrored.Rounded.X import at
 * each call site needs to become KapiIcons.X.
 */
object KapiIcons {
    val ArrowBack: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_arrow_back)

    val ArrowForward: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_arrow_forward)

    val Undo: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_undo)

    val Redo: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_redo)

    val Close: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_close)

    val Check: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_check)

    val CheckCircle: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_check_circle)

    val MoreHoriz: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_more_horiz)

    val MoreVert: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_more_vert)

    val Menu: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_menu)

    val Search: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_search)

    val KeyboardArrowUp: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_keyboard_arrow_up)

    val Draw: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_draw)

    val Brush: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_brush)

    val BorderColor: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_border_color)

    val Backspace: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_backspace)

    val Straighten: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_straighten)

    val Adjust: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_adjust)

    val Gesture: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_gesture)

    val TouchApp: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_touch_app)

    val DoNotTouch: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_do_not_touch)

    val AutoAwesome: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_auto_awesome)

    val CropFree: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_crop_free)

    val ChangeHistory: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_change_history)

    val CropSquare: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_crop_square)

    val RadioButtonUnchecked: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_radio_button_unchecked)

    val RadioButtonChecked: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_radio_button_checked)

    val Crop: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_crop)

    val Image: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_image)

    val TextFields: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_text_fields)

    val EmojiEmotions: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_emoji_emotions)

    val Mic: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_mic)

    val Layers: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_layers)

    val Description: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_description)

    val InsertDriveFile: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_insert_drive_file)

    val PictureAsPdf: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_picture_as_pdf)

    val Folder: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_folder)

    val Book: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_book)

    val Bookmark: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_bookmark)

    val BookmarkBorder: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_bookmark_border)

    val Bookmarks: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_bookmarks)

    val ContentCopy: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_content_copy)

    val Delete: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_delete)

    val Edit: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_edit)

    val Label: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_label)

    val Star: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_star)

    val Info: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_info)

    val Fullscreen: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_fullscreen)

    val FullscreenExit: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_fullscreen_exit)

    val Visibility: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_visibility)

    val VisibilityOff: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_visibility_off)

    val RotateRight: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_rotate_right)

    val Flip: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_flip)

    val FormatColorFill: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_format_color_fill)

    val CloudDownload: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_cloud_download)

    val CloudSync: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_cloud_sync)

    val Sync: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_sync)

    val Share: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_share)

    val Link: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_link)

    val UploadFile: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_upload_file)

    val Add: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_add)

    val Remove: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_remove)

    val PlayArrow: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_play_arrow)

    val Pause: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_pause)

    val Settings: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_settings)

    val Translate: ImageVector
        @Composable get() = ImageVector.vectorResource(id = R.drawable.ic_kapi_translate)

}
