import config from './config.json'

const getPageResults = async (input) => {
    var res = await fetch(`http://${config.server_host}:${config.server_port}/search?query=${input}`,{
        method : `GET`,
        // mode: `no-cors`
    })
    return res.json()
}

export {
    getPageResults
}