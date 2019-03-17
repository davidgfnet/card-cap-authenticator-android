package com.example.bankcap.activities;

import android.os.Bundle;

import com.example.bankcap.BaseActivity;
import com.example.bankcap.R;
import com.example.bankcap.fragments.AdvancedFragment;
import com.example.bankcap.fragments.CalculationFragment;
import com.google.android.material.tabs.TabLayout;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

public class CalculationActivity extends BaseActivity {

    private AdvancedFragment advancedFragment;
    private CalculationFragment calculationFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_calculation);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setupViewPager();
    }

    private void setupViewPager() {
        ViewPager fragmentPager = findViewById(R.id.pager);

        ScreenSlidePagerAdapter mPagerAdapter = new ScreenSlidePagerAdapter(
                getSupportFragmentManager());
        fragmentPager.setAdapter(mPagerAdapter);

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(fragmentPager);
    }

    public void writeLog(String log) {
        advancedFragment.writeLogString(log);
    }

    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {

        ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);

            calculationFragment = new CalculationFragment();
            advancedFragment = new AdvancedFragment();
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
                return calculationFragment;
            }
            return advancedFragment;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0) {
                return getResources().getString(R.string.tab_calculation);
            }
            return getResources().getString(R.string.tab_advanced);
        }
    }
}
