package com.example.flashcards.ui.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AlertDialogLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.flashcards.AddFlashcardActivity
import com.example.flashcards.R
import com.example.flashcards.db.directory.Directory
import com.example.flashcards.db.flashcard.Flashcard
import com.example.flashcards.ui.directories.DirectoriesViewModel
import com.example.flashcards.ui.flashcard_review.FlashcardReviewScreen
import com.example.flashcards.ui.notifications.NotificationEvent
import com.example.flashcards.ui.notifications.NotificationsViewModel
import kotlinx.android.synthetic.main.activity_main.*
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle

class HomeFragment : Fragment() {

    companion object {
        fun newInstance() = HomeFragment()
    }

    private lateinit var directoriesViewModel: DirectoriesViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var notificationsViewModel: NotificationsViewModel
    private lateinit var homeAdapter: HomeAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var homeListener: HomeListener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.home_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        directoriesViewModel = ViewModelProvider(this).get(DirectoriesViewModel::class.java)
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        notificationsViewModel = ViewModelProvider(this).get(NotificationsViewModel::class.java)

        observeViewModel()

        setUpRecyclerView()

        setUpViews()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1000 && resultCode == Activity.RESULT_OK) {
            if (data != null) {

                val flashcard = Flashcard(
                    id = 0,
                    title = data.extras?.get("ADD_FLASHCARD_TITLE_RESULT").toString(),
                    description = data.extras?.get("ADD_FLASHCARD_DESCRIPTION_RESULT").toString(),
                    creation_date = OffsetDateTime.now().format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                    ),
                    last_modified = OffsetDateTime.now().format(
                        DateTimeFormatter.ofLocalizedDateTime(
                            FormatStyle.MEDIUM,
                            FormatStyle.MEDIUM
                        ).withZone(ZoneId.systemDefault())
                    ),
                    directory_id = null
                )
                homeViewModel.send(FlashcardEvent.AddFlashcard(flashcard))
                notificationsViewModel.send(NotificationEvent.AddFlashcard(flashcard))
            } else {
                Toast.makeText(context, "Error, no data.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Error, please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setUpRecyclerView() {
        recyclerView = requireActivity().findViewById(R.id.recyclerView_home)
        recyclerView.layoutManager = LinearLayoutManager(this.context)
        homeListener = object : HomeListener {
            override fun itemDeleted(id: Int) {
                alertToDelete(id)
            }

            override fun addFlashcardToDirectory(id: Int) {
                addToDirectory(id)
            }
        }
        homeAdapter = HomeAdapter(homeListener)

        recyclerView.adapter = homeAdapter
    }

    private fun addToDirectory(id: Int) {
        alertToAdd()

        homeViewModel.send(FlashcardEvent.AddToDirectory(id, 1)) // TODO: get directory id
    }

    private fun getDirectories(): MutableList<Directory> {
        val directoriesToAdd: MutableList<Directory> = mutableListOf()

        directoriesViewModel.allDirectories.observe(
            viewLifecycleOwner,
            Observer { directory ->
                directory.forEach { dir ->
                    directoriesToAdd.add(dir).also {
                        Log.i(
                            "DIRECTORY",
                            dir.title
                        )
                    }
                }
            })

        return directoriesToAdd
    }

    private fun setUpViews() {
        val addFlashcardButton: Button =
            requireActivity().findViewById(R.id.add_flashcard_button)
        val deleteAll: Button = requireActivity().findViewById(R.id.delete_all_button)
        val review: Button = requireActivity().findViewById(R.id.review_flashcards_button)

        addFlashcardButton.setOnClickListener {
            //AddFlashcardActivity.openAddFlashcardActivity(this.requireActivity()) TODO: Doesn't work.
            val intent = Intent(activity, AddFlashcardActivity::class.java)
            startActivityForResult(intent, 1000)
        }

        deleteAll.setOnClickListener {
            alertToDelete()
        }

        review.setOnClickListener {
            val intent = Intent(activity, FlashcardReviewScreen::class.java)
            startActivity(intent)
        }
    }

    private fun alertToAdd() {
        val dialogBuilder = AlertDialog.Builder(requireContext())

        //dialogBuilder.setTitle("Select directory to add")
        //dialogBuilder.setView(R.layout.add_to_directory)

        val directories = getDirectories()
        val dialog = dialogBuilder.create()

        dialog.setTitle("Select directory to add")
        dialog.setContentView(R.layout.add_to_directory)

        dialog.show()

        // val radioGroup: RadioGroup? = dialog.findViewById(R.id.addToDirectoryRadioGroup)
        val radioGroup = RadioGroup(this.context)

        directories.forEach { directory ->
            val radioButton = RadioButton(this.context)
            radioButton.text = directory.title
            radioGroup.addView(radioButton)
            //dialog.addContentView(radioButton, RadioGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            dialog.setContentView(radioGroup)
        }
    }

    private fun alertToDelete() {
        val dialogBuilder = AlertDialog.Builder(requireContext())

        dialogBuilder.setTitle("Are you sure you want to delete ALL?")

        dialogBuilder.setPositiveButton("Yes") { dialog, which ->
            homeViewModel.send(
                FlashcardEvent.DeleteAll
            )
            Toast.makeText(context, "Deleted all.", Toast.LENGTH_SHORT).show()
        }

        dialogBuilder.setNegativeButton("No") { dialog, which ->
            homeViewModel.send(FlashcardEvent.Load)
        }

        dialogBuilder.create().show()
    }

    private fun alertToDelete(id: Int) {
        val dialogBuilder = AlertDialog.Builder(requireContext())

        dialogBuilder.setTitle("Are you sure you want to delete this?")

        dialogBuilder.setPositiveButton("Yes") { dialog, which ->
            homeViewModel.send(
                FlashcardEvent.DeleteFlashcard(id)
            )
            Toast.makeText(context, "Deleted flashcard.", Toast.LENGTH_SHORT).show()
        }

        dialogBuilder.setNegativeButton("No") { dialog, which ->
            homeViewModel.send(FlashcardEvent.Load)
        }

        dialogBuilder.create().show()
    }

    private fun observeViewModel() {
        homeViewModel.state.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is FlashcardState.Error -> showError(state.error)
                is FlashcardState.Success -> showFlashcards(state.flashcards)
            }
        })
    }

    private fun showError(error: Throwable) {
        Log.i("SHOW_ERROR", "Error: ", error)
        Toast.makeText(context, "Error!", Toast.LENGTH_SHORT).show()
    }

    private fun showFlashcards(flashcards: List<Flashcard>) {
        homeAdapter.submitList(flashcards)
    }
}
