package com.visiboard.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.visiboard.app.R;

public class AdminReportsFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_reports, container, false);
        
        tabLayout = view.findViewById(R.id.reportsTabLayout);
        viewPager = view.findViewById(R.id.reportsViewPager);
        
        setupViewPager();
        
        return view;
    }

    private void setupViewPager() {
        ReportsPagerAdapter adapter = new ReportsPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                     if (position == 0) tab.setText("Reported Notes");
                     else tab.setText("Reported Users");
                }
        ).attach();
    }

    private static class ReportsPagerAdapter extends FragmentStateAdapter {
        public ReportsPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) return new ReportedNotesFragment();
            else return new ReportedUsersFragment();
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
