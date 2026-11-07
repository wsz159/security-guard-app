package com.example.securityguard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val apps: MutableList<AppInfo>,
    private val onUninstallClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val pkg: TextView = view.findViewById(R.id.app_package)
        val riskTag: TextView = view.findViewById(R.id.app_risk_tag)
        val uninstallBtn: Button = view.findViewById(R.id.btn_uninstall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.appName
        holder.pkg.text = app.packageName

        if (app.riskyPermissions.isNotEmpty()) {
            holder.riskTag.visibility = View.VISIBLE
            holder.riskTag.text = "敏感权限: ${app.riskyPermissions.joinToString("、")}"
        } else {
            holder.riskTag.visibility = View.GONE
        }

        // 系统核心应用禁止卸载按钮可用性由具体设备策略决定，这里仅做提示
        holder.uninstallBtn.isEnabled = true
        holder.uninstallBtn.text = if (app.isSystemApp) "卸载(系统)" else "卸载"

        holder.uninstallBtn.setOnClickListener {
            onUninstallClick(app)
        }
    }

    override fun getItemCount(): Int = apps.size

    fun removeApp(packageName: String) {
        val index = apps.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            apps.removeAt(index)
            notifyItemRemoved(index)
        }
    }
}
