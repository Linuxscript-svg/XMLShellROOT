package com.root.terminal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class TerminalActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "TerminalActivity"
        private const val REQUEST_STORAGE_PERMISSION = 100
        private const val SCRIPT_PATH = "/sdcard/rootes/Terminal/main.sh"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查并请求存储权限
        if (checkStoragePermission()) {
            initializeTerminal()
        }
    }
    
    /**
     * 检查存储权限
     */
    private fun checkStoragePermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_STORAGE_PERMISSION
            )
            false
        }
    }
    
    /**
     * 处理权限请求结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initializeTerminal()
                } else {
                    showErrorDialog("需要存储权限才能访问Shell脚本")
                }
            }
        }
    }
    
    /**
     * 初始化终端环境
     */
    private fun initializeTerminal() {
        // 获取存储空间信息
        val storageInfo = getStorageInfo()
        Log.d(TAG, "存储信息: $storageInfo")
        
        // 检查并执行脚本
        checkAndExecuteScript()
    }
    
    /**
     * 获取存储空间信息
     */
    private fun getStorageInfo(): String {
        return try {
            val statFs = StatFs(Environment.getExternalStorageDirectory().path)
            
            // 获取存储块大小（API 18+）
            val blockSize = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                statFs.blockSizeLong
            } else {
                statFs.blockSize.toLong()
            }
            
            // 获取可用块数量（API 18+）
            val availableBlocks = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                statFs.availableBlocksLong
            } else {
                statFs.availableBlocks.toLong()
            }
            
            // 获取总块数量（API 18+）
            val totalBlocks = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                statFs.blockCountLong
            } else {
                statFs.blockCount.toLong()
            }
            
            val totalSize = totalBlocks * blockSize
            val availableSize = availableBlocks * blockSize
            
            formatStorageSize(totalSize, availableSize)
        } catch (e: Exception) {
            Log.e(TAG, "获取存储信息失败", e)
            "无法获取存储信息: ${e.message}"
        }
    }
    
    /**
     * 格式化存储空间大小
     */
    private fun formatStorageSize(total: Long, available: Long): String {
        val formatSize = { size: Long ->
            when {
                size >= 1_000_000_000 -> "${String.format("%.2f", size / 1_000_000_000.0)} GB"
                size >= 1_000_000 -> "${String.format("%.2f", size / 1_000_000.0)} MB"
                size >= 1_000 -> "${String.format("%.2f", size / 1_000.0)} KB"
                else -> "$size B"
            }
        }
        
        return "总空间: ${formatSize(total)}\n可用空间: ${formatSize(available)}"
    }
    
    /**
     * 检查并执行脚本
     */
    private fun checkAndExecuteScript() {
        val scriptFile = File(SCRIPT_PATH)
        
        if (!scriptFile.exists()) {
            showErrorDialog("无法访问Shell脚本\n路径: $SCRIPT_PATH\n请确保文件存在")
            return
        }
        
        if (!scriptFile.canRead()) {
            // 尝试使用root权限执行
            executeWithRoot()
        } else {
            executeScriptNormally()
        }
    }
    
    /**
     * 正常方式执行脚本
     */
    private fun executeScriptNormally() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("sh $SCRIPT_PATH")
                val exitCode = process.waitFor()
                
                // 读取输出
                val inputReader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                
                val output = StringBuilder()
                var line: String?
                
                while (inputReader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                
                while (errorReader.readLine().also { line = it } != null) {
                    output.append("[ERROR] ").append(line).append("\n")
                }
                
                inputReader.close()
                errorReader.close()
                
                runOnUiThread {
                    showResultDialog(
                        "脚本执行完成",
                        "退出代码: $exitCode\n\n输出:\n${output.toString()}"
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "执行脚本失败", e)
                runOnUiThread {
                    showErrorDialog("执行脚本失败: ${e.message}")
                }
            }
        }.start()
    }
    
    /**
     * 使用root权限执行脚本
     */
    private fun executeWithRoot() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su -c sh $SCRIPT_PATH")
                
                // 检查是否有root权限
                val outputStream = process.outputStream
                outputStream.write("id\n".toByteArray())
                outputStream.flush()
                outputStream.write("exit\n".toByteArray())
                outputStream.flush()
                
                val exitCode = process.waitFor()
                
                // 读取输出
                val inputReader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                
                val output = StringBuilder()
                var line: String?
                
                // 检查root权限
                while (inputReader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                    if (line?.contains("uid=0") == true) {
                        // 有root权限，重新执行脚本
                        executeScriptWithRoot()
                        return@Thread
                    }
                }
                
                // 没有root权限
                runOnUiThread {
                    showErrorDialog("没有root权限，无法执行脚本")
                }
                
                inputReader.close()
                errorReader.close()
                
            } catch (e: Exception) {
                Log.e(TAG, "检查root权限失败", e)
                runOnUiThread {
                    showErrorDialog("检查root权限失败: ${e.message}")
                }
            }
        }.start()
    }
    
    /**
     * 使用root权限执行脚本（确认有root权限后）
     */
    private fun executeScriptWithRoot() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su -c sh $SCRIPT_PATH")
                val exitCode = process.waitFor()
                
                val inputReader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                
                val output = StringBuilder()
                var line: String?
                
                while (inputReader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                
                while (errorReader.readLine().also { line = it } != null) {
                    output.append("[ROOT ERROR] ").append(line).append("\n")
                }
                
                inputReader.close()
                errorReader.close()
                
                runOnUiThread {
                    showResultDialog(
                        "脚本执行完成 (Root模式)",
                        "退出代码: $exitCode\n\n输出:\n${output.toString()}"
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "使用root执行脚本失败", e)
                runOnUiThread {
                    showErrorDialog("使用root执行脚本失败: ${e.message}")
                }
            }
        }.start()
    }
    
    /**
     * 显示错误对话框
     */
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("错误")
            .setMessage(message)
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 显示结果对话框
     */
    private fun showResultDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * 检查是否具有root权限
     */
    private fun checkRootPermission(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = process.outputStream
            val inputStream = process.inputStream
            
            outputStream.write("id\n".toByteArray())
            outputStream.flush()
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()
            
            process.waitFor()
            
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            var hasRoot = false
            
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("uid=0") == true) {
                    hasRoot = true
                    break
                }
            }
            
            reader.close()
            hasRoot
        } catch (e: Exception) {
            Log.e(TAG, "检查root权限异常", e)
            false
        }
    }
}
