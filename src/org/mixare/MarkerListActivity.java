/*
 * Copyright (C) 2010- Peer internet solutions
 * 
 * This file is part of mixare.
 * 
 * This program is free software: you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, either version 3 of the License, or 
 * (at your option) any later version. 
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details. 
 * 
 * You should have received a copy of the GNU General Public License along with 
 * this program. If not, see <http://www.gnu.org/licenses/>
 */
package org.mixare;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import org.mixare.map.MapActivity;

public class MarkerListActivity extends MaterialDrawerMenuActivity {
    private static final int MENU_MAPVIEW_ID = 0;
    private static final int MENU_SEARCH_ID = 1;
    private EditText editText;
    private MenuItem search;

    private MarkerListFragment markerListFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        markerListFragment=new MarkerListFragment();
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.add(R.id.drawermenu_content_camerascreen, markerListFragment);

        editText = new EditText(this);

        String intentAction=this.getIntent().getAction();

		if (intentAction!=null && intentAction.equals(Intent.ACTION_SEARCH)) {
			// Get search query from IntentExtras
			String query = this.getIntent().getStringExtra(SearchManager.QUERY);
            markerListFragment.updateList(query);
			editText.setText(query);
		} else {
            // MarkerListFragment is started directly
            markerListFragment.createList(null);
		}
        fragmentTransaction.commit();

        if(getActionBar()!=null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Open mapView
		menu.add(MENU_MAPVIEW_ID, MENU_MAPVIEW_ID, MENU_MAPVIEW_ID, "MapView")
				.setIcon(android.R.drawable.ic_menu_mapmode)
				.setShowAsAction(
                        MenuItem.SHOW_AS_ACTION_IF_ROOM
                                | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

		// The editText to use for search
		editText.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.WRAP_CONTENT));
		editText.setHint(getString(R.string.list_view_search_hint));
		// Show the keyboard
		editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (hasFocus) {
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

                } else {
                    imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                }
            }
        });
		// Search at typing
		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,	int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable edit) {
				// Recreate the list
                markerListFragment.updateList(edit.toString());
			}
		});

		// Create a ActionBarItem which adds a editText to the ActionBar used for search
		search = menu.add(MENU_SEARCH_ID, MENU_SEARCH_ID, MENU_SEARCH_ID, getString(R.string.list_view_search_hint));
		search.setIcon(android.R.drawable.ic_menu_search);
		search.setActionView(editText);
		search.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS
				             | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// ActionBarIcon pressed
                finish();
			break;
		case MENU_MAPVIEW_ID:
			// Start MapActivity to choose which Map to start
			Intent map = new Intent(MarkerListActivity.this, MapActivity.class);
			startActivity(map);
			break;
		case MENU_SEARCH_ID:
			// give focus to searchTextBox to open Keyboard
			editText.requestFocus();
		}
		return true;
	}

	@Override
	public boolean onSearchRequested() {
		// Open searchBox and request focus to open Keyboard
		//search.expandActionView(); //TODO reimplement afterSherlockRemove
		editText.requestFocus();
		return false;
	}
}