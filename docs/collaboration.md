# Collaboration

As Reify Health owns the repo and @flyingmachine is the main
contributor but not a Reify employee, here are collaboration
guidelines:

* The `develop` branch deploys snapshot releases.
* @flyingmachine will do his best to create small, incremental,
  feature-focused PRs with `develop` as the base and add
  @codonnell-reify and @manderson202 as reviewers. If for whatever
  reason I @flyingmachine needs to merge the PR and it hasn't been
  reviewed, he'll go ahead and merge without review.
* We'll cut stable releases by first creating a PR to merge `develop`
  into `master`. The Reify team will be able to verify what works and
  what needs updating using the snapshot release, and provide code
  feedback if that hasn't been done. @flyingmachine will have
  discretion to merge these release, with the caveat that changes to
  the v2 API should be discussed with the Reify team.
* I'd like for PR review to begin within a week. Once everything looks
  good, we'll merge into master, circleci will deploy the release,
  and we'll all party :D
* After creating a release, we’ll update the `develop` branch by
  incrementing the version number and appending -SNAPSHOT
* @flyingmachine continue putting together docs without necessarily
  creating PRs for them
