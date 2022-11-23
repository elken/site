;; Copyright © 2022, JUXT LTD.

(ns juxt.pass.form-based-auth-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [malli.core :as malli]
   [juxt.pass.resources.form-based-auth :as form-based-auth]
   [juxt.pass.resources.example-users :as example-users]
   [juxt.pass.resources.session-scope :as session-scope]
   [juxt.pass.resources.user :as user]
   [juxt.test.util :refer [*handler* with-fixtures with-resources with-system-xt with-handler]]))

(use-fixtures :each with-system-xt with-handler)

;;deftest login-with-form-test

(with-fixtures
  (with-resources
    ^{:dependency-graphs
      #{session-scope/dependency-graph
        form-based-auth/dependency-graph
        example-users/dependency-graph
        user/dependency-graph}}
    #{"https://site.test/login"
      "https://site.test/session-scopes/default"
      "https://site.test/users/alice"
      "https://site.test/permissions/system/put-user-identity"
      "https://site.test/user-identities/alice"}

    (let [result (form-based-auth/login-with-form!
                  *handler*
                  :juxt.site/uri "https://site.test/login"
                  "username" "ALICE"
                  "password" "garden")]
      result
      ;;(is (malli/validate [:map [:juxt.pass/session-token :string]] result))
      )))
