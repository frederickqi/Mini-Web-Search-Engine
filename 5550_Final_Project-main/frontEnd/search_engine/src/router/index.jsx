import Search from "../pages/Search";
import Results from "../pages/Results";

const mainRoutes = [
    {
        path:"/",
        element:<Search />
    },

    { 
        path:"/results",
        element: <Results/>
    }
]

export default mainRoutes