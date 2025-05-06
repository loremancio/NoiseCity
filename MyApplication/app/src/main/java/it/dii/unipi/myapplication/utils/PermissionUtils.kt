package it.dii.unipi.myapplication.utils

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Utility class for handling runtime permissions
 */
object PermissionUtils {
    
    /**
     * Checks if the permission is granted
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == 
                PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Requests permission from an Activity
     */
    fun requestPermission(activity: Activity, permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
    }
    
    /**
     * Requests permission from a Fragment
     */
    fun requestPermission(fragment: Fragment, permission: String, requestCode: Int) {
        fragment.requestPermissions(arrayOf(permission), requestCode)
    }
}