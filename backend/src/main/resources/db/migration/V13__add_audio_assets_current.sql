alter table audiobook_project
    add column if not exists audio_assets_current boolean not null default false;
