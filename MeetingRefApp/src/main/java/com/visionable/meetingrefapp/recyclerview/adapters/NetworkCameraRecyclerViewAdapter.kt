package com.visionable.meetingrefapp.recyclerview.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.visionable.meetingrefapp.data.NetworkCameraItem
import com.visionable.meetingrefapp.databinding.NetworkCameraListItemBinding
import com.visionable.meetingrefapp.fragments.MeetingFragment
import com.visionable.meetingsdk.MeetingSDK

/**
 * Custom [RecyclerView.Adapter] to display the network cameras configured by the local participant
 *
 * @property networkCameraStreamList -> [List<NetworkCameraItem>]: Full list of items to display
 * @property fragment -> [MeetingFragment]: Used to display Toasts
 */
class NetworkCameraRecyclerViewAdapter(
    private val networkCameraStreamList: MutableList<NetworkCameraItem>,
    private val fragment: MeetingFragment
) : RecyclerView.Adapter<NetworkCameraRecyclerViewAdapter.NetworkCameraRecyclerViewHolder>() {

    inner class NetworkCameraRecyclerViewHolder(
        val binding: NetworkCameraListItemBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NetworkCameraRecyclerViewHolder {
        val itemView = NetworkCameraListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return NetworkCameraRecyclerViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: NetworkCameraRecyclerViewHolder, position: Int) {
        with (holder.binding) {
			networkCameraStreamList[position].displayName?.let {
			    networkCameraDisplayName.visibility = View.VISIBLE
                networkCameraDisplayName.text = it
            }
            networkCameraUrl.text = networkCameraStreamList[position].camURL

			closeBtn.setOnClickListener {
				disableNetworkVideo(position)
            }
        }
    }

    private fun disableNetworkVideo(position: Int) {
        MeetingSDK.disableNetworkVideo(networkCameraStreamList[position].camURL)

        networkCameraStreamList.removeAt(position)
        notifyItemRemoved(position)
    }

    override fun getItemCount(): Int {
        return networkCameraStreamList.size
    }

}